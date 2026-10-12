# DOCKET — Deployment

## Option A — Single Web Service on Render (recommended)

One Docker image runs **Spring Boot + the React production build** on the same origin (`https://your-service.onrender.com`). API calls use `/api/...`. MySQL is a separate managed database.

### 1. Create a MySQL database

On Render (or any host), create a MySQL instance and note host, port, database name, user, and password.

Flyway runs on startup with `JPA_DDL_AUTO=validate` and **does not** drop existing data. Schema changes only apply via forward migrations under `backend/src/main/resources/db/migration/`.

### 2. Create a Web Service

| Setting | Value |
|---------|--------|
| Environment | **Docker** |
| Dockerfile path | `./Dockerfile` (repository root) |
| Docker context | Repository root |
| Health check path | `/api/health` |

### 3. Environment variables

Copy from `deploy/render.env.example`. Minimum for a successful start under the `prod` profile:

- `JWT_SECRET` — `openssl rand -base64 48`
- `COOKIE_SECURE=true`
- `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` — your public **https://** URL
- `FORWARD_HEADERS_STRATEGY=native`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (and `DB_SSL=true` if the provider requires TLS)
- `MAIL_HOST` and related SMTP settings — must be a real SMTP server (not localhost/MailHog)

Optional: Google Calendar and `EXTENSION_ORIGINS` (see README).

### 4. Custom domain (optional)

Point DNS at Render, then set `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, and `GOOGLE_REDIRECT_URI` to that domain.

### 5. Verify

```bash
curl -fsS https://YOUR_SERVICE.onrender.com/api/health
# open the site URL in a browser — React shell loads; login uses same-origin /api
```

---

## Option B — Self-hosted Docker Compose (Caddy + separate backend)

For a VPS with automatic HTTPS via Caddy:

```bash
cp deploy/.env.production.example deploy/.env.production
# fill DOMAIN, secrets, SMTP, DB passwords
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.production up -d --build
```

This stack uses `deploy/web.Dockerfile` (Caddy) and `backend/Dockerfile` (API only). Prefer **Option A** on Render.

---

## Local single-container test

```bash
# From repository root
docker build -t docket .

docker run --rm -p 8080:8080 \
  -e PORT=8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e COOKIE_SECURE=false \
  -e FRONTEND_URL=http://localhost:8080 \
  -e CORS_ALLOWED_ORIGINS=http://localhost:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=docket \
  -e DB_USER=docket \
  -e DB_PASSWORD=change-me \
  -e MAIL_HOST=smtp.example.com \
  -e MAIL_PORT=587 \
  -e MAIL_SMTP_AUTH=true \
  -e MAIL_STARTTLS=true \
  -e MAIL_FROM=no-reply@example.com \
  docket
```

Note: with `SPRING_PROFILES_ACTIVE=prod`, `ProductionGuard` still requires HTTPS URLs and a non-local mail host. For a looser local smoke test, omit `SPRING_PROFILES_ACTIVE=prod` (or use `dev`) so the guard does not run:

```bash
docker run --rm -p 8080:8080 \
  -e PORT=8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e COOKIE_SECURE=false \
  -e FRONTEND_URL=http://localhost:8080 \
  -e CORS_ALLOWED_ORIGINS=http://localhost:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PASSWORD=change-me \
  docket
```

---

## Security reminders

- Never commit real `.env` files or dashboard secrets.
- Prefer Render secret groups / encrypted env vars.
- Back up MySQL and any persistent upload volume regularly.
