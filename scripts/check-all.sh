#!/bin/sh
# Runs every automated check that needs no database, no Maven and no network (besides `npm ci`).
# From the repository root:  sh scripts/check-all.sh
set -e
echo "== 1/6 static audit (no seed data, no secrets, ownership, accessibility basics) =="
node scripts/audit.mjs
echo; echo "== 2/6 backend business-rule checks (plain javac, no Spring) =="
(cd backend && sh verify/run-checks.sh)
echo; echo "== 3/6 Chrome extension extractor checks =="
[ -d frontend/node_modules ] || (cd frontend && npm ci --no-audit --no-fund)
(cd extension && NODE_PATH="$(cd ../frontend/node_modules && pwd)" node test/extract.test.js)
echo; echo "== 4/6 frontend tests =="
(cd frontend && npm test --silent)
echo; echo "== 5/6 frontend production build =="
(cd frontend && npm run build --silent)
echo; echo "== 6/6 frontend/backend parity (priority score + skill match, thousands of boundary cases through both) =="
node --test scripts/parity.test.mjs
echo; echo "All automated checks passed. On a machine with MySQL: mvn test / mvn spring-boot:run, then node --test e2e/ (see TESTING.md)."
