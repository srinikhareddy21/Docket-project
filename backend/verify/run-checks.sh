#!/bin/sh
# Plain-Java checks for the pure logic (no Maven, no Spring, no network needed).
# Needs a JDK 21 with javac. Run from the backend/ folder:  sh verify/run-checks.sh
set -e
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Dfile.encoding=UTF-8}"
OUT=$(mktemp -d)
SRC=src/main/java/com/docket
javac -encoding UTF-8 -d "$OUT" $SRC/util/MiniJson.java $SRC/autofill/UrlGuard.java $SRC/autofill/SafeFetcher.java $SRC/autofill/JobPageParser.java \
  $SRC/calendar/TokenCrypto.java $SRC/calendar/CalendarPlanner.java $SRC/calendar/GoogleClient.java $SRC/calendar/SyncEngine.java \
  $SRC/applications/Vocab.java $SRC/applications/Priority.java $SRC/applications/Normalizer.java $SRC/applications/Csv.java \
  $SRC/skills/SkillMatcher.java $SRC/documents/FileSniffer.java $SRC/reminders/ReminderPlanner.java \
  $SRC/notifications/NotificationPlanner.java $SRC/auth/PasswordPolicy.java \
  $SRC/security/SlidingWindowLimiter.java $SRC/dashboard/Metrics.java \
  verify/com/docket/autofill/AutofillChecks.java verify/com/docket/calendar/CalendarChecks.java verify/com/docket/core/CoreChecks.java
java -cp "$OUT" com.docket.autofill.AutofillChecks
java -cp "$OUT" com.docket.calendar.CalendarChecks
java -cp "$OUT" com.docket.core.CoreChecks
# Production start-up guard: compiled against tiny annotation stubs (verify/stubs), so no Spring jars are needed.
OUT2=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT2" $(find verify/stubs -name '*.java') $SRC/config/AppProperties.java $SRC/calendar/TokenCrypto.java $SRC/ops/ProductionGuard.java verify/com/docket/ops/OpsChecks.java
java -cp "$OUT2" com.docket.ops.OpsChecks
rm -rf "$OUT2"
rm -rf "$OUT"
