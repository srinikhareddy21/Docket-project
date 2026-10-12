package com.docket.autofill;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Fetches one public web page with a hand-rolled HTTP/1.1 GET so that:
 *  - the TCP connection goes to the exact IP address UrlGuard already vetted (no second DNS lookup, so no rebinding),
 *  - TLS still verifies the certificate against the real hostname and sends it as SNI,
 *  - redirects are followed manually (max 4) and every hop goes through UrlGuard again, never https -> http,
 *  - size, time and content type are bounded. No cookies or credentials are ever sent.
 * Not a Spring bean on purpose: pure logic, testable with plain javac.
 */
public final class SafeFetcher {
  public static final int MAX_BODY_BYTES = 1_500_000;
  private static final int MAX_REDIRECTS = 4;
  private static final int MAX_HEADER_BYTES = 32 * 1024;
  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 8_000;
  private static final long TOTAL_DEADLINE_MS = 15_000;
  private static final String USER_AGENT = "DOCKET-Autofill/1.0 (+job details fetched at the request of the signed-in user)";

  public enum Kind { INVALID, BLOCKED, UNREACHABLE, TIMEOUT, TOO_MANY_REDIRECTS, UNSUPPORTED_CONTENT, TLS_FAILED }

  public static final class FetchException extends Exception {
    private final Kind kind;
    public FetchException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
  }

  /** status is the final HTTP status. body is empty for non-2xx. */
  public record Page(int status, String html, URI finalUri, boolean truncated) {}

  private final UrlGuard guard;

  public SafeFetcher(UrlGuard guard) { this.guard = guard; }

  public Page fetch(String rawUrl) throws FetchException {
    long deadline = System.nanoTime() + TOTAL_DEADLINE_MS * 1_000_000L;
    UrlGuard.Target target;
    try { target = guard.check(rawUrl); }
    catch (UrlGuard.Rejected r) { throw new FetchException(r.problem() == UrlGuard.Problem.BLOCKED ? Kind.BLOCKED : Kind.INVALID, r.getMessage()); }

    for (int hop = 0; ; hop++) {
      Response r = request(target, deadline);
      if (r.status >= 300 && r.status < 400 && r.status != 304 && r.location != null) {
        if (hop >= MAX_REDIRECTS) throw new FetchException(Kind.TOO_MANY_REDIRECTS, "The link redirects too many times.");
        URI next;
        try { next = target.uri().resolve(r.location.trim()); }
        catch (IllegalArgumentException e) { throw new FetchException(Kind.INVALID, "The link redirects to an invalid address."); }
        if (target.tls() && "http".equalsIgnoreCase(next.getScheme())) throw new FetchException(Kind.BLOCKED, "The link redirects from a secure to an insecure address.");
        try { target = guard.check(next); }
        catch (UrlGuard.Rejected rej) { throw new FetchException(Kind.BLOCKED, "The link redirects to an address that cannot be fetched."); }
        continue;
      }
      if (r.status < 200 || r.status >= 300) return new Page(r.status, "", target.uri(), false);
      String ct = r.contentType == null ? "" : r.contentType.toLowerCase(Locale.ROOT);
      if (!ct.isEmpty() && !ct.startsWith("text/html") && !ct.startsWith("application/xhtml")) {
        throw new FetchException(Kind.UNSUPPORTED_CONTENT, "This link is not a web page.");
      }
      return new Page(r.status, new String(r.body, charsetOf(ct)), target.uri(), r.truncated);
    }
  }

  private record Response(int status, String location, String contentType, byte[] body, boolean truncated) {}

  private Response request(UrlGuard.Target t, long deadline) throws FetchException {
    Socket socket = null;
    try {
      socket = connect(t);
      socket.setSoTimeout(READ_TIMEOUT_MS);
      OutputStream out = socket.getOutputStream();
      URI u = t.uri();
      String path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
      String hostHeader = t.asciiHost().contains(":") ? "[" + t.asciiHost() + "]" : t.asciiHost();
      if (t.port() != (t.tls() ? 443 : 80)) hostHeader += ":" + t.port();
      String req = "GET " + path + " HTTP/1.1\r\nHost: " + hostHeader + "\r\nUser-Agent: " + USER_AGENT
          + "\r\nAccept: text/html,application/xhtml+xml;q=0.9\r\nAccept-Language: en\r\nAccept-Encoding: gzip, identity\r\nConnection: close\r\n\r\n";
      out.write(req.getBytes(StandardCharsets.US_ASCII));
      out.flush();
      InputStream in = socket.getInputStream();

      String statusLine = readLine(in, deadline);
      if (statusLine == null || !statusLine.startsWith("HTTP/")) throw new FetchException(Kind.UNREACHABLE, "The website sent an unexpected reply.");
      String[] sp = statusLine.split(" ", 3);
      int status;
      try { status = Integer.parseInt(sp[1].trim()); } catch (RuntimeException e) { throw new FetchException(Kind.UNREACHABLE, "The website sent an unexpected reply."); }

      Map<String, String> h = new HashMap<>();
      int headerBytes = 0;
      while (true) {
        String line = readLine(in, deadline);
        if (line == null) throw new FetchException(Kind.UNREACHABLE, "The website closed the connection early.");
        if (line.isEmpty()) break;
        headerBytes += line.length();
        if (headerBytes > MAX_HEADER_BYTES) throw new FetchException(Kind.UNSUPPORTED_CONTENT, "The website sent too much header data.");
        int c = line.indexOf(':');
        if (c > 0) h.putIfAbsent(line.substring(0, c).trim().toLowerCase(Locale.ROOT), line.substring(c + 1).trim());
      }
      if (status >= 300 && status < 400) return new Response(status, h.get("location"), null, new byte[0], false);
      if (status < 200 || status >= 300) return new Response(status, null, null, new byte[0], false);

      String te = h.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
      boolean chunked = te.contains("chunked");
      long length = -1;
      if (!chunked && h.containsKey("content-length")) {
        try { length = Long.parseLong(h.get("content-length").trim()); } catch (NumberFormatException ignored) { }
      }
      boolean[] truncated = {false};
      byte[] raw = chunked ? readChunked(in, deadline, truncated) : readBody(in, length, deadline, truncated);
      String enc = h.getOrDefault("content-encoding", "identity").toLowerCase(Locale.ROOT).trim();
      if (enc.equals("gzip") || enc.equals("x-gzip")) {
        raw = gunzip(raw, truncated);
      } else if (!enc.equals("identity") && !enc.isEmpty()) {
        throw new FetchException(Kind.UNSUPPORTED_CONTENT, "The website replied in a format we cannot read.");
      }
      return new Response(status, null, h.get("content-type"), raw, truncated[0]);
    } catch (SocketTimeoutException e) {
      throw new FetchException(Kind.TIMEOUT, "The website took too long to respond.");
    } catch (javax.net.ssl.SSLException e) {
      throw new FetchException(Kind.TLS_FAILED, "We could not make a secure connection to the website.");
    } catch (IOException e) {
      throw new FetchException(Kind.UNREACHABLE, "We could not reach the website.");
    } finally {
      if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
  }

  private Socket connect(UrlGuard.Target t) throws IOException, FetchException {
    IOException last = null;
    int tries = 0;
    for (InetAddress a : t.addresses()) {
      if (tries++ >= 3) break;
      Socket raw = new Socket();
      try {
        raw.connect(new InetSocketAddress(a, t.port()), CONNECT_TIMEOUT_MS);
        if (!t.tls()) return raw;
        raw.setSoTimeout(READ_TIMEOUT_MS);
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, t.asciiHost(), t.port(), true);
        SSLParameters p = ssl.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");                     // certificate must match the hostname
        if (!t.asciiHost().contains(":") && !t.asciiHost().matches("[0-9.]+")) p.setServerNames(List.of(new SNIHostName(t.asciiHost())));
        ssl.setSSLParameters(p);
        ssl.startHandshake();
        return ssl;
      } catch (IOException e) {
        last = e;
        try { raw.close(); } catch (IOException ignored) { }
      }
    }
    throw last != null ? last : new IOException("no address");
  }

  private static String readLine(InputStream in, long deadline) throws IOException, FetchException {
    StringBuilder b = new StringBuilder();
    while (true) {
      checkDeadline(deadline);
      int c = in.read();
      if (c < 0) return b.length() == 0 ? null : b.toString();
      if (c == '\n') { int n = b.length(); if (n > 0 && b.charAt(n - 1) == '\r') b.setLength(n - 1); return b.toString(); }
      b.append((char) c);
      if (b.length() > 8192) throw new FetchException(Kind.UNSUPPORTED_CONTENT, "The website sent an unexpected reply.");
    }
  }

  private static byte[] readBody(InputStream in, long length, long deadline, boolean[] truncated) throws IOException, FetchException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long remaining = length < 0 ? Long.MAX_VALUE : length;
    while (remaining > 0) {
      checkDeadline(deadline);
      int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
      if (n < 0) break;
      if (out.size() + n > MAX_BODY_BYTES) { out.write(buf, 0, MAX_BODY_BYTES - out.size()); truncated[0] = true; break; }
      out.write(buf, 0, n);
      remaining -= n;
    }
    return out.toByteArray();
  }

  private static byte[] readChunked(InputStream in, long deadline, boolean[] truncated) throws IOException, FetchException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while (true) {
      String sizeLine = readLine(in, deadline);
      if (sizeLine == null) break;
      int semi = sizeLine.indexOf(';');
      String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
      if (hex.isEmpty()) continue;
      long size;
      try { size = Long.parseLong(hex, 16); } catch (NumberFormatException e) { throw new FetchException(Kind.UNREACHABLE, "The website sent an unexpected reply."); }
      if (size == 0) break;
      byte[] buf = new byte[8192];
      long left = size;
      while (left > 0) {
        checkDeadline(deadline);
        int n = in.read(buf, 0, (int) Math.min(buf.length, left));
        if (n < 0) return out.toByteArray();
        if (out.size() + n > MAX_BODY_BYTES) { out.write(buf, 0, MAX_BODY_BYTES - out.size()); truncated[0] = true; return out.toByteArray(); }
        out.write(buf, 0, n);
        left -= n;
      }
      readLine(in, deadline); // CRLF after each chunk
    }
    return out.toByteArray();
  }

  /** Decompression is bounded too, so a tiny gzip bomb cannot exhaust memory. */
  private static byte[] gunzip(byte[] data, boolean[] truncated) throws IOException {
    try (GZIPInputStream g = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = g.read(buf)) > 0) {
        if (out.size() + n > MAX_BODY_BYTES) { out.write(buf, 0, MAX_BODY_BYTES - out.size()); truncated[0] = true; break; }
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } catch (java.io.EOFException e) {
      return data.length == 0 ? data : new byte[0]; // cut-off gzip stream: nothing usable
    }
  }

  private static void checkDeadline(long deadline) throws FetchException {
    if (System.nanoTime() > deadline) throw new FetchException(Kind.TIMEOUT, "The website took too long to respond.");
  }

  private static Charset charsetOf(String contentType) {
    int i = contentType.indexOf("charset=");
    if (i >= 0) {
      String name = contentType.substring(i + 8).replace("\"", "").replace("'", "");
      int end = name.indexOf(';');
      if (end >= 0) name = name.substring(0, end);
      try { return Charset.forName(name.trim()); } catch (RuntimeException ignored) { }
    }
    return StandardCharsets.UTF_8;
  }
}
