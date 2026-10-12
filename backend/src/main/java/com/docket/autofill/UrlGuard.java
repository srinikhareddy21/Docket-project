package com.docket.autofill;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Function;

/**
 * SSRF defence for server-side fetching of user-supplied URLs. Pure logic (no Spring).
 * A URL is acceptable only if: http(s), no credentials in the URL, port 80/443, a real hostname or public IP,
 * and EVERY address the name resolves to is a public, routable address. The caller must then connect to the
 * address returned here (not resolve again), which closes the DNS-rebinding gap.
 */
public final class UrlGuard {
  public static final int MAX_URL_LENGTH = 2048;

  public enum Problem { INVALID, BLOCKED }

  public static final class Rejected extends Exception {
    private final Problem problem;
    public Rejected(Problem p, String message) { super(message); this.problem = p; }
    public Problem problem() { return problem; }
  }

  /** A vetted destination. connectTo is already resolved and already checked. */
  public record Target(URI uri, String asciiHost, int port, boolean tls, InetAddress[] addresses) {}

  private final Function<String, InetAddress[]> resolver;
  private final boolean allowLoopbackForTests;

  public UrlGuard() { this(UrlGuard::systemResolve, false); }

  private static InetAddress[] systemResolve(String host) {
    try { return InetAddress.getAllByName(host); } catch (UnknownHostException e) { throw new UncheckedUnknownHost(e); }
  }

  /** Test seam only. Production code uses the no-arg constructor. */
  UrlGuard(Function<String, InetAddress[]> resolver, boolean allowLoopbackForTests) {
    this.resolver = resolver;
    this.allowLoopbackForTests = allowLoopbackForTests;
  }

  public Target check(String raw) throws Rejected {
    if (raw == null || raw.isBlank()) throw new Rejected(Problem.INVALID, "Paste a job posting link.");
    String text = raw.trim();
    if (text.length() > MAX_URL_LENGTH) throw new Rejected(Problem.INVALID, "This link is too long.");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c <= 0x20 || c == 0x7f) throw new Rejected(Problem.INVALID, "This is not a valid web address.");
    }
    URI uri;
    try { uri = new URI(text); } catch (Exception e) { throw new Rejected(Problem.INVALID, "This is not a valid web address."); }
    return check(uri);
  }

  public Target check(URI uri) throws Rejected {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) throw new Rejected(Problem.INVALID, "Only http and https links are supported.");
    if (uri.getRawUserInfo() != null) throw new Rejected(Problem.INVALID, "Links that contain a username or password are not supported.");
    String host = uri.getHost();
    if (host == null || host.isBlank()) throw new Rejected(Problem.INVALID, "This is not a valid web address.");
    boolean tls = scheme.equals("https");
    int port = uri.getPort() == -1 ? (tls ? 443 : 80) : uri.getPort();
    if (!allowLoopbackForTests && port != 80 && port != 443) throw new Rejected(Problem.BLOCKED, "Links with a custom port cannot be fetched.");

    String ascii;
    try {
      String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
      ascii = h.contains(":") ? h : IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) { throw new Rejected(Problem.INVALID, "This is not a valid web address."); }
    if (ascii.endsWith(".")) ascii = ascii.substring(0, ascii.length() - 1);

    if (!allowLoopbackForTests && isInternalName(ascii)) throw blocked();

    InetAddress[] addrs;
    try { addrs = resolver.apply(ascii); }
    catch (UncheckedUnknownHost e) { throw new Rejected(Problem.INVALID, "We could not find that website."); }
    if (addrs == null || addrs.length == 0) throw new Rejected(Problem.INVALID, "We could not find that website.");
    for (InetAddress a : addrs) {
      if (!isPublic(a, allowLoopbackForTests)) throw blocked();
    }
    return new Target(uri, ascii, port, tls, addrs);
  }

  private static Rejected blocked() { return new Rejected(Problem.BLOCKED, "This address cannot be fetched."); }

  static boolean isInternalName(String h) {
    if (h.equals("localhost") || h.endsWith(".localhost")) return true;
    for (String suffix : new String[] {".local", ".internal", ".localdomain", ".lan", ".home", ".corp", ".intranet", ".private"}) {
      if (h.endsWith(suffix)) return true;
    }
    return false;
  }

  /** True only for addresses that are globally routable unicast. */
  public static boolean isPublic(InetAddress a, boolean allowLoopback) {
    if (a.isLoopbackAddress()) return allowLoopback;
    if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
    byte[] b = a.getAddress();
    if (a instanceof Inet4Address) return publicV4(b);
    if (a instanceof Inet6Address) return publicV6(b);
    return false;
  }

  private static boolean publicV4(byte[] b) {
    int o1 = b[0] & 0xff, o2 = b[1] & 0xff, o3 = b[2] & 0xff;
    if (o1 == 0 || o1 == 10 || o1 == 127) return false;
    if (o1 == 100 && o2 >= 64 && o2 <= 127) return false;            // CGNAT 100.64/10
    if (o1 == 169 && o2 == 254) return false;                        // link-local, cloud metadata
    if (o1 == 172 && o2 >= 16 && o2 <= 31) return false;
    if (o1 == 192 && o2 == 0 && (o3 == 0 || o3 == 2)) return false;  // 192.0.0.0/24, TEST-NET-1
    if (o1 == 192 && o2 == 168) return false;
    if (o1 == 192 && o2 == 88 && o3 == 99) return false;             // 6to4 relay anycast
    if (o1 == 198 && (o2 == 18 || o2 == 19)) return false;           // benchmarking
    if (o1 == 198 && o2 == 51 && o3 == 100) return false;            // TEST-NET-2
    if (o1 == 203 && o2 == 0 && o3 == 113) return false;             // TEST-NET-3
    if (o1 >= 224) return false;                                     // multicast + reserved + broadcast
    return true;
  }

  private static boolean publicV6(byte[] b) {
    int first = b[0] & 0xff, second = b[1] & 0xff;
    if ((first & 0xfe) == 0xfc) return false;                        // fc00::/7 unique local
    if (first == 0xfe && (second & 0xc0) == 0x80) return false;      // fe80::/10 link-local
    if (first == 0xfe && (second & 0xc0) == 0xc0) return false;      // fec0::/10 deprecated site-local
    if (first == 0x20 && second == 0x02) return false;               // 2002::/16 6to4 (embeds an IPv4)
    if (first == 0x20 && second == 0x01 && b[2] == 0x00 && b[3] == 0x00) return false; // Teredo
    if (first == 0x20 && second == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // documentation
    if (first == 0x01 && second == 0x00 && b[2] == 0 && b[3] == 0 && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0) return false; // discard 100::/64
    boolean zero96 = true;
    for (int i = 0; i < 12; i++) if (b[i] != 0) { zero96 = false; break; }
    if (zero96) return false;                                        // ::/96 incl. IPv4-compatible and ::1
    boolean nat64 = first == 0x00 && second == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
    if (nat64) {
      byte[] v4 = {b[12], b[13], b[14], b[15]};
      return publicV4(v4);                                           // 64:ff9b::/96 embeds an IPv4
    }
    return (first & 0xe0) == 0x20;                                   // only global unicast 2000::/3
  }

  /** Thrown by the default resolver so lookups can be a plain Function. */
  public static final class UncheckedUnknownHost extends RuntimeException {
    public UncheckedUnknownHost(UnknownHostException e) { super(e.getMessage()); }
  }
}
