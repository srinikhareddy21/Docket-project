package com.docket.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, dependency-free JSON reader/writer. Used for untrusted third-party JSON (job pages, Google responses)
 * so the pure logic can be tested without any framework. Depth and size are bounded.
 * Values: Map, List, String, Double/Long (as Number), Boolean, null.
 */
public final class MiniJson {
  private static final int MAX_DEPTH = 40;
  private final String s;
  private int i;

  private MiniJson(String s) { this.s = s; }

  public static final class ParseException extends RuntimeException {
    public ParseException(String m) { super(m); }
  }

  /** Returns the parsed value, or throws ParseException. */
  public static Object parse(String text) {
    if (text == null) throw new ParseException("empty");
    MiniJson p = new MiniJson(text);
    p.ws();
    Object v = p.value(0);
    p.ws();
    if (p.i != p.s.length()) throw new ParseException("trailing data");
    return v;
  }

  /** Never throws: returns null when the text is not valid JSON. */
  public static Object tryParse(String text) {
    try { return parse(text); } catch (RuntimeException e) { return null; }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object o) { return o instanceof Map<?, ?> ? (Map<String, Object>) o : null; }
  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object o) { return o instanceof List<?> ? (List<Object>) o : null; }
  public static String str(Object o) {
    if (o instanceof String x) return x;
    if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
    return null;
  }

  private Object value(int depth) {
    if (depth > MAX_DEPTH) throw new ParseException("too deep");
    if (i >= s.length()) throw new ParseException("unexpected end");
    char c = s.charAt(i);
    switch (c) {
      case '{': return object(depth);
      case '[': return array(depth);
      case '"': return string();
      case 't': expect("true"); return Boolean.TRUE;
      case 'f': expect("false"); return Boolean.FALSE;
      case 'n': expect("null"); return null;
      default: return number();
    }
  }

  private Map<String, Object> object(int depth) {
    Map<String, Object> m = new LinkedHashMap<>();
    i++; ws();
    if (peek() == '}') { i++; return m; }
    while (true) {
      ws();
      if (peek() != '"') throw new ParseException("key expected");
      String k = string();
      ws();
      if (peek() != ':') throw new ParseException("colon expected");
      i++; ws();
      m.put(k, value(depth + 1));
      ws();
      char c = peek();
      i++;
      if (c == ',') continue;
      if (c == '}') return m;
      throw new ParseException("bad object");
    }
  }

  private List<Object> array(int depth) {
    List<Object> l = new ArrayList<>();
    i++; ws();
    if (peek() == ']') { i++; return l; }
    while (true) {
      ws();
      l.add(value(depth + 1));
      ws();
      char c = peek();
      i++;
      if (c == ',') continue;
      if (c == ']') return l;
      throw new ParseException("bad array");
    }
  }

  private String string() {
    StringBuilder b = new StringBuilder();
    i++; // opening quote
    while (true) {
      if (i >= s.length()) throw new ParseException("unterminated string");
      char c = s.charAt(i++);
      if (c == '"') return b.toString();
      if (c == '\\') {
        if (i >= s.length()) throw new ParseException("bad escape");
        char e = s.charAt(i++);
        switch (e) {
          case '"': b.append('"'); break;
          case '\\': b.append('\\'); break;
          case '/': b.append('/'); break;
          case 'b': b.append('\b'); break;
          case 'f': b.append('\f'); break;
          case 'n': b.append('\n'); break;
          case 'r': b.append('\r'); break;
          case 't': b.append('\t'); break;
          case 'u':
            if (i + 4 > s.length()) throw new ParseException("bad unicode escape");
            try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
            catch (NumberFormatException ex) { throw new ParseException("bad unicode escape"); }
            i += 4; break;
          default: throw new ParseException("bad escape");
        }
      } else if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
        throw new ParseException("control character");
      } else b.append(c);
    }
  }

  private Object number() {
    int start = i;
    while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
    if (start == i) throw new ParseException("unexpected character");
    String t = s.substring(start, i);
    try {
      if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) return Long.parseLong(t);
      return Double.parseDouble(t);
    } catch (NumberFormatException e) { throw new ParseException("bad number"); }
  }

  private void expect(String w) {
    if (!s.startsWith(w, i)) throw new ParseException("unexpected token");
    i += w.length();
  }
  private char peek() { if (i >= s.length()) throw new ParseException("unexpected end"); return s.charAt(i); }
  private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

  // ---- writing ----

  /** Serializes Map/List/String/Number/Boolean/null. Strings are fully escaped. */
  public static String write(Object o) {
    StringBuilder b = new StringBuilder();
    write(o, b);
    return b.toString();
  }

  private static void write(Object o, StringBuilder b) {
    if (o == null) { b.append("null"); return; }
    if (o instanceof String x) { quote(x, b); return; }
    if (o instanceof Number || o instanceof Boolean) { b.append(o); return; }
    if (o instanceof Map<?, ?> m) {
      b.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (!first) b.append(',');
        first = false;
        quote(String.valueOf(e.getKey()), b);
        b.append(':');
        write(e.getValue(), b);
      }
      b.append('}');
      return;
    }
    if (o instanceof Iterable<?> it) {
      b.append('[');
      boolean first = true;
      for (Object x : it) { if (!first) b.append(','); first = false; write(x, b); }
      b.append(']');
      return;
    }
    quote(String.valueOf(o), b);
  }

  private static void quote(String x, StringBuilder b) {
    b.append('"');
    for (int k = 0; k < x.length(); k++) {
      char c = x.charAt(k);
      switch (c) {
        case '"': b.append("\\\""); break;
        case '\\': b.append("\\\\"); break;
        case '\n': b.append("\\n"); break;
        case '\r': b.append("\\r"); break;
        case '\t': b.append("\\t"); break;
        default:
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
      }
    }
    b.append('"');
  }
}
