# End-to-end API tests

Run against a **running** backend on a development or staging database (they create and then delete throwaway accounts):

    cd backend && docker compose up -d && mvn spring-boot:run      # terminal 1
    E2E_BASE_URL=http://localhost:8080 node --test e2e/            # terminal 2 (Node 20+, no install needed)

Optional, enables the "undo window passed" and "share link expired" checks (they edit two timestamps through SQL):

    E2E_SQL_CMD='docker compose -f backend/docker-compose.yml exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" docket' \
    E2E_BASE_URL=http://localhost:8080 node --test e2e/

The password-recovery test reads the real reset email from MailHog (started by `docker compose up -d`; default `http://localhost:8025`, override with `E2E_MAILHOG_URL`). If MailHog is not reachable that one test is reported as skipped, not passed.

Notes: credential endpoints allow 30 requests per 10 minutes per IP and this suite makes about 35. Start the backend for test runs with a higher limit (the production default is unchanged):

    RATE_LIMIT_IP_MAX=500 mvn spring-boot:run

or wait ten minutes between runs. The brute-force test also needs a fresh backend per run (the login-failure counter is in memory).
The brute-force and account-deletion tests run last on purpose.
This suite could not be executed in the environment where it was written (no Spring jars, no MySQL). Treat the first run as the real verification.
