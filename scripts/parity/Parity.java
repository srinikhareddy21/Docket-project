import com.docket.applications.Priority;
import com.docket.skills.SkillMatcher;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads scenarios from stdin, prints the BACKEND's answer for each. frontend/tests/parity.test.mjs feeds the same
 * scenarios to the frontend functions and requires identical results. Lines (fields separated by '|', '~' = null):
 *   P|now|status|deadline|followUpDate|statusChangedAt|nextInterviewAt|prepIncomplete
 *   S|userSkills(csv)|requiredSkills(csv)
 */
public class Parity {
  static String n(String s) { return "~".equals(s) ? null : s; }
  static List<String> csv(String s) { List<String> o = new ArrayList<>(); if (s != null && !s.isEmpty() && !s.equals("~")) for (String p : s.split(",", -1)) o.add(p); return o; }

  public static void main(String[] a) throws Exception {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    StringBuilder out = new StringBuilder();
    for (String line; (line = in.readLine()) != null; ) {
      String[] f = line.split("\\|", -1);
      if (f[0].equals("P")) {
        Instant now = Instant.parse(f[1]);
        Priority.Result r = Priority.compute(f[2], n(f[3]) == null ? null : Instant.parse(f[3]), n(f[4]) == null ? null : LocalDate.parse(f[4]),
            n(f[5]) == null ? null : Instant.parse(f[5]), n(f[6]) == null ? null : Instant.parse(f[6]), f[7].equals("1"), now, now.atZone(ZoneOffset.UTC).toLocalDate());
        out.append(r.score()).append('|').append(r.level()).append('|').append(String.join(";", r.reasons())).append('\n');
      } else {
        SkillMatcher.Result r = SkillMatcher.match(csv(f[1]), csv(f[2]));
        out.append(r.score() == null ? "null" : String.format(java.util.Locale.ROOT, "%.6f", r.score())).append('|').append(String.join(",", r.matched())).append('|').append(String.join(",", r.missing())).append('\n');
      }
    }
    System.out.print(out);
  }
}
