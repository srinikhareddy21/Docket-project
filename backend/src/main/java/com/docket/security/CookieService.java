package com.docket.security;

import com.docket.auth.AuthService;
import com.docket.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Both auth cookies are HttpOnly, SameSite=Lax, and Secure whenever COOKIE_SECURE=true (production). */
@Component
public class CookieService {
  public static final String ACCESS = "docket_at";
  public static final String REFRESH = "docket_rt";
  private final AppProperties props;

  public CookieService(AppProperties props) { this.props = props; }

  public void writeSession(HttpServletResponse res, AuthService.Session s) {
    add(res, build(ACCESS, s.accessToken(), "/api", props.auth().accessTtl()));
    add(res, build(REFRESH, s.refreshToken(), "/api/auth", s.refreshMaxAge())); // null => session cookie
  }

  public void clear(HttpServletResponse res) {
    add(res, build(ACCESS, "", "/api", Duration.ZERO));
    add(res, build(REFRESH, "", "/api/auth", Duration.ZERO));
  }

  public static String read(HttpServletRequest req, String name) {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue();
    return null;
  }

  private ResponseCookie build(String name, String value, String path, Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
        .httpOnly(true).secure(props.cookie().secure()).sameSite("Lax").path(path);
    if (maxAge != null) b.maxAge(maxAge);
    return b.build();
  }

  private void add(HttpServletResponse res, ResponseCookie c) { res.addHeader(HttpHeaders.SET_COOKIE, c.toString()); }
}
