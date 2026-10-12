package com.docket.autofill;

import com.docket.util.MiniJson;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the schema.org JobPosting data (JSON-LD) that job pages publish for search engines. This is the only
 * source: nothing is guessed from page text, so a field is returned only when the page itself states it.
 * Pure logic (no Spring).
 */
public final class JobPageParser {
  private JobPageParser() {}

  public record Parsed(boolean found, Map<String, Object> fields, List<String> warnings) {}

  private static final Pattern LD_JSON = Pattern.compile(
      "<script\\b[^>]*\\btype\\s*=\\s*[\"']?application/ld\\+json[\"']?[^>]*>(.*?)</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final int MAX_BLOCKS = 20;

  public static Parsed parse(String html) {
    List<String> warnings = new ArrayList<>();
    Map<String, Object> posting = null;
    Matcher m = LD_JSON.matcher(html == null ? "" : html);
    int blocks = 0;
    while (m.find() && blocks++ < MAX_BLOCKS && posting == null) {
      String body = m.group(1).trim();
      body = body.replaceAll("^<!--|-->$", "").replaceAll("^//<!\\[CDATA\\[|//\\]\\]>$|^<!\\[CDATA\\[|\\]\\]>$", "").trim();
      Object json = MiniJson.tryParse(body);
      if (json != null) posting = findJobPosting(json, 0);
    }
    if (posting == null) return new Parsed(false, Map.of(), warnings);

    Map<String, Object> f = new LinkedHashMap<>();
    put(f, "position", cap(text(posting.get("title")), 150));
    put(f, "companyName", cap(orgName(posting.get("hiringOrganization")), 150));
    if (!f.containsKey("position") && !f.containsKey("companyName")) return new Parsed(false, Map.of(), warnings);

    put(f, "jobType", jobType(posting.get("employmentType")));
    if ("TELECOMMUTE".equalsIgnoreCase(str(posting.get("jobLocationType")))) f.put("workMode", "Remote");
    put(f, "location", cap(location(posting), 150));
    String desc = htmlToText(str(posting.get("description")));
    put(f, "jobDescription", cap(desc, 20000));
    List<String> skills = skills(posting.get("skills"));
    if (!skills.isEmpty()) f.put("requiredSkills", skills);
    put(f, "salaryOrStipend", cap(salary(posting.get("baseSalary")), 100));

    String through = str(posting.get("validThrough"));
    if (through != null && !through.isBlank()) {
      Instant d = parseDeadline(through.trim(), warnings);
      if (d != null) f.put("deadline", d.toString());
    }
    return new Parsed(true, f, warnings);
  }

  // ---- fallback: Open Graph / HTML metadata ----

  private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ATTR = Pattern.compile("([a-zA-Z:_-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
  private static final Pattern TITLE = Pattern.compile("<title\\b[^>]*>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern H1 = Pattern.compile("<h1\\b[^>]*>(.*?)</h1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern GENERIC_TITLE = Pattern.compile("(?i)(^|\\b)(jobs|careers|job openings|open positions|openings|job search|search results)\\s*$|^(careers?|jobs?)\\b");
  private static final Pattern CANONICAL = Pattern.compile("<link\\b[^>]*rel\\s*=\\s*[\"']?canonical[^>]*>", Pattern.CASE_INSENSITIVE);

  /**
   * Used only when the page has no JobPosting data. Reads what the page itself declares about this URL:
   * og:title / twitter:title / h1 / title (job title), og:site_name (company) and og:description / description.
   * Returns nothing when the page says it is a different (usually generic listing) page via og:url or canonical,
   * because then its title and summary are not about this job. Nothing is guessed from body text.
   */
  public static Parsed parseMeta(String html, java.net.URI pageUri) {
    if (html == null || html.isEmpty()) return new Parsed(false, Map.of(), List.of());
    Map<String, String> meta = new LinkedHashMap<>();
    Matcher m = META.matcher(html);
    while (m.find()) {
      Map<String, String> a = attrs(m.group());
      String key = a.getOrDefault("property", a.get("name"));
      String content = a.get("content");
      if (key != null && content != null && !content.isBlank()) meta.putIfAbsent(key.toLowerCase(Locale.ROOT), decode(content).trim());
    }
    String declared = meta.get("og:url");
    Matcher c = CANONICAL.matcher(html);
    if (declared == null && c.find()) declared = attrs(c.group()).get("href");
    // The page must say which URL it describes, and it must be this one; otherwise it is likely a generic or listing page.
    if (declared == null || pageUri == null || !samePage(declared, pageUri)) return new Parsed(false, Map.of(), List.of());

    String site = meta.get("og:site_name");
    String title = firstNonBlank(meta.get("og:title"), meta.get("twitter:title"), group(H1, html), group(TITLE, html));
    title = stripSiteSuffix(title, site);
    if (title == null || (site != null && title.equalsIgnoreCase(site.trim())) || GENERIC_TITLE.matcher(title).find())
      return new Parsed(false, Map.of(), List.of());
    Map<String, Object> f = new LinkedHashMap<>();
    put(f, "position", cap(title, 150));
    if (!f.containsKey("position")) return new Parsed(false, Map.of(), List.of());
    put(f, "companyName", cap(site, 150));
    put(f, "jobDescription", cap(firstNonBlank(meta.get("og:description"), meta.get("description"), meta.get("twitter:description")), 20000));
    return new Parsed(true, f, List.of("This page does not publish structured job data, so only its title and summary were read. Check the job title and company carefully."));
  }

  private static Map<String, String> attrs(String tag) {
    Map<String, String> a = new LinkedHashMap<>();
    Matcher m = ATTR.matcher(tag);
    while (m.find()) a.put(m.group(1).toLowerCase(Locale.ROOT), m.group(3) != null ? m.group(3) : m.group(4));
    return a;
  }

  static boolean samePage(String declared, java.net.URI page) {
    try {
      java.net.URI d = page.resolve(declared.trim());
      String dp = trimSlash(d.getPath()), pp = trimSlash(page.getPath());
      return d.getHost() != null && d.getHost().equalsIgnoreCase(page.getHost()) && dp.equalsIgnoreCase(pp);
    } catch (Exception e) { return false; }
  }

  private static String trimSlash(String p) { return p == null ? "" : p.replaceAll("/+$", ""); }

  private static String group(Pattern p, String html) {
    Matcher m = p.matcher(html);
    return m.find() ? htmlToText(m.group(1)) : null;
  }

  private static String firstNonBlank(String... v) { for (String s : v) if (s != null && !s.isBlank()) return s.trim(); return null; }

  static String stripSiteSuffix(String title, String site) {
    if (title == null) return null;
    String t = title.trim();
    if (site != null && !site.isBlank()) {
      for (String sep : new String[] {" | ", " - ", " \u2013 ", " \u2014 ", " at "}) {
        String suffix = sep + site.trim();
        if (t.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) { t = t.substring(0, t.length() - suffix.length()).trim(); break; }
      }
    }
    return t.isEmpty() ? null : t;
  }

  // ---- locating the posting ----

  @SuppressWarnings("unchecked")
  static Map<String, Object> findJobPosting(Object node, int depth) {
    if (depth > 6 || node == null) return null;
    Map<String, Object> map = MiniJson.asMap(node);
    if (map != null) {
      if (isJobPosting(map.get("@type"))) return map;
      Object graph = map.get("@graph");
      if (graph != null) { Map<String, Object> r = findJobPosting(graph, depth + 1); if (r != null) return r; }
      return null;
    }
    List<Object> list = MiniJson.asList(node);
    if (list != null) for (Object o : list) { Map<String, Object> r = findJobPosting(o, depth + 1); if (r != null) return r; }
    return null;
  }

  private static boolean isJobPosting(Object type) {
    if (type instanceof String s) return s.equalsIgnoreCase("JobPosting") || s.toLowerCase(Locale.ROOT).endsWith("/jobposting");
    List<Object> l = MiniJson.asList(type);
    if (l != null) for (Object o : l) if (isJobPosting(o)) return true;
    return false;
  }

  // ---- field mapping ----

  private static void put(Map<String, Object> f, String k, String v) { if (v != null && !v.isBlank()) f.put(k, v); }

  private static String str(Object o) { return MiniJson.str(o); }

  private static String text(Object o) {
    String s = str(o);
    if (s == null) return null;
    String t = htmlToText(s);
    return t.isBlank() ? null : t.replaceAll("\\s+", " ");
  }

  private static String cap(String s, int max) {
    if (s == null) return null;
    String t = s.strip();
    return t.length() <= max ? t : t.substring(0, max).stripTrailing();
  }

  private static String orgName(Object o) {
    Map<String, Object> m = MiniJson.asMap(o);
    if (m != null) return text(m.get("name"));
    List<Object> l = MiniJson.asList(o);
    if (l != null && !l.isEmpty()) return orgName(l.get(0));
    return text(o);
  }

  static String jobType(Object o) {
    List<String> vals = new ArrayList<>();
    List<Object> l = MiniJson.asList(o);
    if (l != null) for (Object x : l) { String s = str(x); if (s != null) vals.add(s); }
    else if (str(o) != null) vals.add(str(o));
    for (String raw : vals) {
      String v = raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      if (v.contains("INTERN")) return "Internship";
      if (v.equals("FULL_TIME") || v.equals("FULLTIME")) return "Full-time";
      if (v.equals("PART_TIME") || v.equals("PARTTIME")) return "Part-time";
      if (v.equals("CONTRACTOR") || v.equals("TEMPORARY") || v.equals("VOLUNTEER") || v.equals("PER_DIEM") || v.equals("OTHER")) return "Other";
    }
    return null;
  }

  private static String location(Map<String, Object> posting) {
    Set<String> parts = new LinkedHashSet<>();
    Object jl = posting.get("jobLocation");
    List<Object> locs = MiniJson.asList(jl);
    if (locs == null) { locs = new ArrayList<>(); if (jl != null) locs.add(jl); }
    for (Object lo : locs) {
      if (parts.size() >= 3) break;
      Map<String, Object> lm = MiniJson.asMap(lo);
      if (lm == null) { String s = text(lo); if (s != null) parts.add(s); continue; }
      Object addr = lm.get("address");
      Map<String, Object> am = MiniJson.asMap(addr);
      if (am == null) { String s = text(addr); if (s == null) s = text(lm.get("name")); if (s != null) parts.add(s); continue; }
      List<String> seg = new ArrayList<>();
      for (String k : new String[] {"addressLocality", "addressRegion"}) { String v = text(am.get(k)); if (v != null) seg.add(v); }
      Object country = am.get("addressCountry");
      String c = MiniJson.asMap(country) != null ? text(MiniJson.asMap(country).get("name")) : text(country);
      if (c != null) seg.add(c);
      if (!seg.isEmpty()) parts.add(String.join(", ", seg));
    }
    if (parts.isEmpty()) {
      Object req = posting.get("applicantLocationRequirements");
      List<Object> rl = MiniJson.asList(req);
      if (rl == null) { rl = new ArrayList<>(); if (req != null) rl.add(req); }
      for (Object r : rl) { Map<String, Object> rm = MiniJson.asMap(r); String n = rm != null ? text(rm.get("name")) : text(r); if (n != null && parts.size() < 3) parts.add(n); }
    }
    return parts.isEmpty() ? null : String.join("; ", parts);
  }

  static List<String> skills(Object o) {
    List<String> raw = new ArrayList<>();
    List<Object> l = MiniJson.asList(o);
    if (l != null) {
      for (Object x : l) {
        Map<String, Object> xm = MiniJson.asMap(x);
        String s = xm != null ? str(xm.get("name")) : str(x);
        if (s != null) raw.add(s);
      }
    } else if (MiniJson.asMap(o) != null) {
      String s = str(MiniJson.asMap(o).get("name"));
      if (s != null) raw.add(s);
    } else if (str(o) != null) {
      raw.add(str(o));
    }
    Set<String> out = new LinkedHashSet<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String r : raw) {
      for (String piece : htmlToText(r).split("[,;\\n\\u2022|]")) {
        String t = piece.replaceAll("^[\\s\\-*]+", "").strip();
        if (t.isEmpty() || t.length() > 80) continue;
        if (seen.add(t.toLowerCase(Locale.ROOT))) out.add(t);
        if (out.size() >= 50) return new ArrayList<>(out);
      }
    }
    return new ArrayList<>(out);
  }

  private static String salary(Object o) {
    Map<String, Object> m = MiniJson.asMap(o);
    if (m == null) return null;
    String cur = str(m.get("currency"));
    Object v = m.get("value");
    Map<String, Object> vm = MiniJson.asMap(v);
    String min = null, max = null, unit = null;
    if (vm != null) {
      min = num(vm.get("minValue")); max = num(vm.get("maxValue"));
      if (min == null && max == null) { min = num(vm.get("value")); }
      unit = str(vm.get("unitText"));
    } else if (v instanceof Number || (v instanceof String s && s.matches("[0-9.,]+"))) {
      min = num(v);
    }
    if (min == null && max == null) return null;
    StringBuilder b = new StringBuilder();
    if (cur != null && cur.matches("[A-Za-z]{3}")) b.append(cur.toUpperCase(Locale.ROOT)).append(' ');
    b.append(min != null ? min : max);
    if (min != null && max != null && !min.equals(max)) b.append('\u2013').append(max);
    if (unit != null && unit.matches("[A-Za-z ]{2,12}")) b.append(" per ").append(unit.toLowerCase(Locale.ROOT));
    return b.toString();
  }

  private static String num(Object o) {
    if (o == null) return null;
    String s = str(o);
    if (s == null) return null;
    try {
      double d = Double.parseDouble(s.replace(",", ""));
      if (d == Math.rint(d) && Math.abs(d) < 1e12) return String.format(Locale.US, "%,d", (long) d);
      return String.format(Locale.US, "%,.2f", d);
    } catch (NumberFormatException e) { return null; }
  }

  static Instant parseDeadline(String s, List<String> warnings) {
    try { return Instant.parse(s); } catch (RuntimeException ignored) { }
    try { return OffsetDateTime.parse(s).toInstant(); } catch (RuntimeException ignored) { }
    try {
      Instant i = LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
      warnings.add("The page gave the closing time without a time zone. It is shown as UTC; check it.");
      return i;
    } catch (RuntimeException ignored) { }
    try {
      LocalDate d = LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
      warnings.add("The page gave only a closing date, so the deadline is set to the end of that day (UTC). Check the time.");
      return d.plusDays(1).atStartOfDay().minusSeconds(1).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ignored) { }
    warnings.add("The closing date on the page could not be read, so no deadline was filled in.");
    return null;
  }

  // ---- HTML to text ----

  private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");

  static String htmlToText(String s) {
    if (s == null) return "";
    String t = s;
    // The text may be HTML, escaped HTML (&lt;p&gt;), or plain. Decode once, then strip tags.
    if (!t.contains("<") && t.contains("&lt;")) t = decode(t);
    t = t.replaceAll("(?is)<(script|style)\\b.*?</\\1\\s*>", " ");
    t = t.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|h[1-6]|tr|ul|ol)\\s*>", "\n\n").replaceAll("(?i)<li\\b[^>]*>", "\n- ").replaceAll("(?i)</li\\s*>", "");
    t = t.replaceAll("(?s)<[^>]{0,2000}>", "");
    t = decode(t);
    t = t.replace('\u00a0', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n");
    return t.strip();
  }

  private static String decode(String s) {
    Matcher m = ENTITY.matcher(s);
    StringBuilder b = new StringBuilder();
    while (m.find()) {
      String g = m.group(1);
      String rep;
      switch (g) {
        case "amp": rep = "&"; break;
        case "lt": rep = "<"; break;
        case "gt": rep = ">"; break;
        case "quot": rep = "\""; break;
        case "apos": rep = "'"; break;
        case "nbsp": rep = " "; break;
        case "ndash": rep = "\u2013"; break;
        case "mdash": rep = "\u2014"; break;
        case "rsquo": rep = "\u2019"; break;
        case "lsquo": rep = "\u2018"; break;
        case "hellip": rep = "\u2026"; break;
        default:
          rep = m.group();
          try {
            int cp = g.startsWith("#x") ? Integer.parseInt(g.substring(2), 16) : g.startsWith("#") ? Integer.parseInt(g.substring(1)) : -1;
            if (cp > 0 && cp <= 0x10FFFF && !Character.isISOControl(cp) || cp == '\n') rep = new String(Character.toChars(cp));
          } catch (RuntimeException ignored) { }
      }
      m.appendReplacement(b, Matcher.quoteReplacement(rep));
    }
    m.appendTail(b);
    return b.toString();
  }
}
