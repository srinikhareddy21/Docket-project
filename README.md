# DOCKET

**DOCKET** is a full-stack job-application tracker for students and early-career job seekers. It helps you organize applications, interviews, deadlines, skills, and follow-ups in one place—with reminders, analytics, and optional Google Calendar and Chrome extension support.

> This repository is a working application suitable for local development, evaluation, and portfolio review. Optional integrations (Google Calendar, SMTP email) require your own credentials and have not been fully verified in every environment.

---

## Features

- **Applications** — Create, edit, search, filter, and soft-delete applications; Kanban board; status history; CSV export; duplicate detection
- **Interviews** — Multiple rounds per application (date/time, mode, notes, outcome); dedicated Interviews view
- **Dashboard & analytics** — Counts, needs-action list, priority scoring, funnel, trends, skill-match distribution
- **Skills** — Profile skills matched against per-application requirements
- **Reminders** — Automatic deadline / interview / follow-up reminders; optional email delivery
- **In-app notifications** — Persistent notifications with unread badge and mark-as-read
- **Job URL autofill** — Server-side page fetch with SSRF protections
- **Chrome extension** — Extract job details from the current page and save to your account (token-authenticated API)
- **Google Calendar** (optional) — OAuth2 connect; sync interviews, follow-ups, and deadlines when configured
- **Sharing** — Time-limited progress share links
- **Auth** — Register / login, JWT in HttpOnly cookies, password reset, account deletion request

---

## Tech stack

| Layer | Technology |
|--------|------------|
| **Frontend** | React 18, Vite, Tailwind CSS, React Router, Recharts, Motion |
| **Backend** | Java 21, Spring Boot 4, Spring Security, Spring Data JPA |
| **Database** | MySQL 8, Flyway migrations |
| **Integrations** | JavaMail (SMTP / MailHog), Google Calendar API (optional), Chrome extension (Manifest V3) |
| **Deploy** | Docker Compose, Caddy (see `DEPLOYMENT.md`) |

---

## Project structure

```text
docket/
├── backend/           Spring Boot API, Flyway migrations, pure-logic verify suite
├── frontend/          React SPA (Vite)
├── extension/         Chrome extension (save job from current tab)
├── deploy/            Production Compose, Caddy, backup scripts
├── e2e/               Black-box API tests (needs running backend)
├── scripts/           check-all, parity tests, static audit
└── .github/workflows/ CI
```

---

## Prerequisites

- **Node.js** 20+ and npm  
- **JDK 21** and **Maven 3.9+**  
- **Docker** (recommended for MySQL and MailHog)  
- Optional: Google Cloud project (Calendar API) for calendar sync  

---

## Local setup

### 1. Database

```bash
cd backend
docker compose up -d
```

This starts MySQL and MailHog. Flyway runs migrations automatically when the API starts.

### 2. Backend environment

```bash
cd backend
cp .env.example .env
```

Edit `.env` and set at least:

| Variable | Notes |
|----------|--------|
| `DB_PASSWORD` / `DB_ROOT_PASSWORD` | Match `docker-compose.yml` |
| `JWT_SECRET` | Required: `openssl rand -base64 48` |
| `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` | Default `http://localhost:5173` |
| `COOKIE_SECURE` | `false` for local HTTP |
| `MAIL_*` | Defaults target MailHog (`localhost:1025`) |

Optional Google Calendar:

```text
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=http://localhost:8080/api/integrations/google-calendar/callback
TOKEN_ENCRYPTION_KEY=   # openssl rand -base64 32
```

Never commit a real `.env` file.

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

API: `http://localhost:8080` · Health: `GET /api/health`

### 4. Run the frontend

```bash
cd frontend
cp .env.example .env    # optional; defaults use Vite proxy to :8080
npm ci
npm run dev
```

App: `http://localhost:5173`

### 5. Chrome extension (optional)

1. In DOCKET: **Settings → Extension** → create a token (shown once).  
2. Chrome → `chrome://extensions` → Developer mode → **Load unpacked** → select the `extension/` folder.  
3. Extension **Settings**: API base `http://localhost:8080`, paste the token, allow host access, **Test connection**.  
4. Open a job page → click the extension → review fields → **Save to DOCKET**.

---

## API overview

Authenticated browser traffic uses session cookies + CSRF. The Chrome extension uses `Authorization: Bearer dkx_…` on `/api/extension/**` only.

| Area | Examples |
|------|----------|
| Auth | `POST /api/auth/register`, `/login`, `/logout`, `/refresh` |
| Applications | `GET/POST /api/applications`, `PUT /api/applications/{id}`, export, autofill |
| Interviews | `GET /api/interviews`, `POST /api/applications/{id}/interviews` |
| Dashboard | `GET /api/dashboard`, `/api/dashboard/actions`, `/api/analytics/*` |
| Reminders / notifications | `/api/reminders`, `/api/notifications` |
| Extension | `GET /api/extension/me`, `POST /api/extension/applications` |
| Google Calendar | `/api/integrations/google-calendar/*` |

---

## Testing

**No database required** (from repo root):

```bash
sh scripts/check-all.sh
```

Runs static audit, backend pure-logic checks (`backend/verify/run-checks.sh`), extension extractor tests, frontend unit tests, production build, and frontend/backend parity checks.

**Backend unit tests:**

```bash
cd backend && mvn test
```

**Frontend only:**

```bash
cd frontend && npm test && npm run build
```

**E2E API suite** (requires running backend + MySQL; see `e2e/README.md`):

```bash
E2E_BASE_URL=http://localhost:8080 node --test e2e/
```

More detail: [TESTING.md](./TESTING.md).

---

## Deployment

**Render (one Web Service):** root `Dockerfile` builds the React app into the Spring Boot JAR so the SPA and API share one URL. Managed MySQL is separate. See **[DEPLOYMENT.md](./DEPLOYMENT.md)** and `deploy/render.env.example`.

Self-hosted Compose with Caddy remains available under `deploy/`. Production start-up (`prod` profile) refuses weak JWT secrets, non-HTTPS frontend URLs, and development mail hosts.

---

## Security notes

- Secrets belong only in `.env` / deployment secrets managers—not in source control.  
- `.gitignore` excludes `.env`, `node_modules/`, `dist/`, `target/`, logs, and local data directories.  
- Passwords are hashed (BCrypt); JWTs are HttpOnly cookies; extension tokens are stored as hashes.  
- Google refresh tokens are encrypted at rest when Calendar is configured.  
- Job autofill validates URLs and blocks private/metadata targets (SSRF protections).  
- Configure SMTP and Google OAuth only with credentials you control.

---

## License

If no license file is present in this repository, all rights remain with the author unless otherwise stated.
