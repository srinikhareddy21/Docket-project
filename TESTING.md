# Testing

## Automated checks (no database)

From the repository root:

```bash
sh scripts/check-all.sh
```

Includes:

1. Static audit (`scripts/audit.mjs`)
2. Backend pure-logic suite (`backend/verify/run-checks.sh`)
3. Chrome extension extractor tests (`extension/test/extract.test.js`)
4. Frontend unit tests (`frontend`: Vitest)
5. Frontend production build
6. Priority / skill-match parity between frontend and backend (`scripts/parity.test.mjs`)

## Backend (Maven)

```bash
cd backend
mvn test          # unit tests (no full Spring context required for BusinessRulesTest)
mvn verify        # if integration profile is configured in your environment
```

## Frontend

```bash
cd frontend
npm ci
npm test
npm run build
```

## End-to-end API tests

Requires a running backend and MySQL (and MailHog for password-reset cases). See [e2e/README.md](./e2e/README.md).

```bash
# Terminal 1
cd backend && docker compose up -d && mvn spring-boot:run

# Terminal 2
E2E_BASE_URL=http://localhost:8080 node --test e2e/
```

For higher rate limits during the suite:

```bash
RATE_LIMIT_IP_MAX=500 mvn spring-boot:run
```

## What is not guaranteed without your environment

- Live Google Calendar OAuth and event sync (needs your Google Cloud credentials)
- Live SMTP delivery outside MailHog
- Full e2e against production data (use a dedicated staging database)

Do not treat skipped or environment-blocked tests as passes.
