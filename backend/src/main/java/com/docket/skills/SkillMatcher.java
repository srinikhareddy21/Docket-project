package com.docket.skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic skill match. score = matched required skills / total required skills x 100.
 * Case, spacing and a small alias table are normalized. No AI. Same rules as frontend/src/utils/skills.js.
 * Required skills are de-duplicated after normalization; no required skills means no score (null).
 */
public final class SkillMatcher {
  private SkillMatcher() {}

  private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("js", "javascript"), Map.entry("ecmascript", "javascript"), Map.entry("ts", "typescript"),
      Map.entry("reactjs", "react"), Map.entry("react.js", "react"), Map.entry("nodejs", "node"), Map.entry("node.js", "node"),
      Map.entry("py", "python"), Map.entry("postgres", "postgresql"), Map.entry("mongo", "mongodb"),
      Map.entry("k8s", "kubernetes"), Map.entry("cpp", "c++"), Map.entry("c plus plus", "c++"),
      Map.entry("springboot", "spring boot"), Map.entry("spring-boot", "spring boot"),
      Map.entry("ds", "data structures"), Map.entry("dsa", "data structures and algorithms"),
      Map.entry("oop", "object oriented programming"), Map.entry("oops", "object oriented programming"),
      Map.entry("ml", "machine learning"), Map.entry("html5", "html"), Map.entry("css3", "css"),
      Map.entry("rest api", "rest"), Map.entry("restful apis", "rest"));

  public record Result(Double score, List<String> matched, List<String> missing, int total) {}

  public static String normalize(String s) {
    String k = s == null ? "" : s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    return ALIASES.getOrDefault(k, k);
  }

  public static Result match(List<String> userSkills, List<String> requiredSkills) {
    Set<String> have = new HashSet<>();
    for (String s : userSkills) have.add(normalize(s));
    Set<String> seen = new HashSet<>();
    List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
    for (String s : requiredSkills) {
      String n = normalize(s);
      if (n.isEmpty() || !seen.add(n)) continue;
      (have.contains(n) ? matched : missing).add(s);
    }
    int total = matched.size() + missing.size();
    if (total == 0) return new Result(null, List.of(), List.of(), 0);
    return new Result(matched.size() * 100.0 / total, matched, missing, total);
  }

  /** Convenience for list rows: just the score, or null when there is not enough skill data. */
  public static Double score(List<String> userSkills, List<String> requiredSkills) {
    return match(userSkills, requiredSkills).score();
  }
}
