package com.docket.calendar;

import com.docket.util.MiniJson;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Minimal Google OAuth2 + Calendar v3 client over plain HTTPS (java.net.http). Only fixed Google hosts are ever
 * contacted (the base URLs come from configuration, never from user input). No redirects are followed, tokens are
 * never logged, and error details are reduced to a short status/reason. Pure logic: testable against a local fake.
 */
public final class GoogleClient {
  public static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";

  public record Config(String clientId, String clientSecret, String redirectUri, String authUrl, String tokenUrl, String revokeUrl, String calendarBase) {
    public static Config google(String id, String secret, String redirect) {
      return new Config(id, secret, redirect, "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
          "https://oauth2.googleapis.com/revoke", "https://www.googleapis.com/calendar/v3");
    }
  }

  public record Tokens(String accessToken, String refreshToken, String scope) {}
  /** status 0 = network failure. reason is a short Google reason code or null. */
  public record Result(int status, String reason) {
    public boolean ok() { return status >= 200 && status < 300; }
    public boolean rateLimited() { return status == 429 || (status == 403 && reason != null && reason.toLowerCase().contains("ratelimit")); }
  }

  public static final class AuthException extends Exception {
    private final boolean revoked;
    public AuthException(String message, boolean revoked) { super(message); this.revoked = revoked; }
    /** True when Google says the grant is gone (user revoked access, or the token expired for good). */
    public boolean grantRevoked() { return revoked; }
  }

  private final Config cfg;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

  public GoogleClient(Config cfg) { this.cfg = cfg; }

  public String authorizationUrl(String state) {
    return cfg.authUrl() + "?" + form(Map.of("client_id", cfg.clientId(), "redirect_uri", cfg.redirectUri(), "response_type", "code",
        "scope", SCOPE, "access_type", "offline", "prompt", "consent", "include_granted_scopes", "false", "state", state));
  }

  public Tokens exchangeCode(String code) throws AuthException {
    Map<String, Object> j = tokenCall(Map.of("code", code, "client_id", cfg.clientId(), "client_secret", cfg.clientSecret(),
        "redirect_uri", cfg.redirectUri(), "grant_type", "authorization_code"));
    String access = MiniJson.str(j.get("access_token"));
    if (access == null) throw new AuthException("Google did not return an access token.", false);
    return new Tokens(access, MiniJson.str(j.get("refresh_token")), MiniJson.str(j.get("scope")));
  }

  public String refreshAccessToken(String refreshToken) throws AuthException {
    Map<String, Object> j = tokenCall(Map.of("refresh_token", refreshToken, "client_id", cfg.clientId(), "client_secret", cfg.clientSecret(), "grant_type", "refresh_token"));
    String access = MiniJson.str(j.get("access_token"));
    if (access == null) throw new AuthException("Google did not return an access token.", false);
    return access;
  }

  /** Best effort. Returns true when Google accepted the revocation. */
  public boolean revoke(String token) {
    try {
      HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(cfg.revokeUrl())).timeout(Duration.ofSeconds(10))
          .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token", token)))).build(),
          HttpResponse.BodyHandlers.ofString());
      return r.statusCode() / 100 == 2;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return false;
    }
  }

  public Result insert(String accessToken, String eventId, Map<String, Object> body) {
    Map<String, Object> withId = new LinkedHashMap<>(body);
    withId.put("id", eventId);
    return send("POST", cfg.calendarBase() + "/calendars/primary/events", accessToken, MiniJson.write(withId));
  }

  public Result update(String accessToken, String eventId, Map<String, Object> body) {
    return send("PUT", cfg.calendarBase() + "/calendars/primary/events/" + URLEncoder.encode(eventId, StandardCharsets.UTF_8), accessToken, MiniJson.write(body));
  }

  public Result delete(String accessToken, String eventId) {
    return send("DELETE", cfg.calendarBase() + "/calendars/primary/events/" + URLEncoder.encode(eventId, StandardCharsets.UTF_8), accessToken, null);
  }

  private Result send(String method, String url, String accessToken, String json) {
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + accessToken).header("Accept", "application/json");
      if (json != null) b.header("Content-Type", "application/json; charset=utf-8").method(method, HttpRequest.BodyPublishers.ofString(json));
      else b.method(method, HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      return new Result(r.statusCode(), r.statusCode() / 100 == 2 ? null : reason(r.body()));
    } catch (IOException e) {
      return new Result(0, "network");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Result(0, "interrupted");
    }
  }

  private Map<String, Object> tokenCall(Map<String, String> params) throws AuthException {
    try {
      HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(cfg.tokenUrl())).timeout(Duration.ofSeconds(10))
          .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(form(params))).build(), HttpResponse.BodyHandlers.ofString());
      Map<String, Object> j = MiniJson.asMap(MiniJson.tryParse(r.body()));
      if (r.statusCode() / 100 != 2 || j == null) {
        String err = j == null ? null : MiniJson.str(j.get("error"));
        throw new AuthException("Google refused the request" + (err != null ? " (" + err + ")" : "") + ".", "invalid_grant".equals(err));
      }
      return j;
    } catch (IOException e) {
      throw new AuthException("Could not reach Google.", false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AuthException("Could not reach Google.", false);
    }
  }

  private static String reason(String body) {
    Map<String, Object> j = MiniJson.asMap(MiniJson.tryParse(body));
    if (j == null) return null;
    Map<String, Object> err = MiniJson.asMap(j.get("error"));
    if (err == null) return null;
    java.util.List<Object> errs = MiniJson.asList(err.get("errors"));
    if (errs != null && !errs.isEmpty() && MiniJson.asMap(errs.get(0)) != null) return MiniJson.str(MiniJson.asMap(errs.get(0)).get("reason"));
    return MiniJson.str(err.get("status"));
  }

  private static String form(Map<String, String> p) {
    StringJoiner j = new StringJoiner("&");
    p.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
    return j.toString();
  }
}
