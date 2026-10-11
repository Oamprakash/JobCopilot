# Apply Copilot

AI job-application copilot: finds well-matched jobs, tailors each application, and autofills forms, while the user always clicks Submit.

This repo is **Phase 1: Foundation**: sign-in, resume upload and parsing, the master profile editor, and job preferences.

```
backend/   Kotlin + Ktor API, Postgres (Flyway), resume parsing (PDFBox, POI, Claude API)
web/       Next.js 14 app: sign-in, onboarding, profile
```

## What works in Phase 1

| Area | What it does |
| --- | --- |
| Magic-link sign-in | Email → one-time link (15 min, single use, 5 per hour per email). Session is a 30-day JWT in an HttpOnly, SameSite=Lax cookie. Only token hashes are stored. |
| Resume upload | PDF or DOCX up to 5 MB, detected by file bytes. Scanned PDFs, password-protected PDFs and other formats get a clear error. |
| Parsing | Claude (forced tool call → profile JSON) when `ANTHROPIC_API_KEY` is set; a rule-based parser otherwise, or if the API call fails. |
| "Never invent" guardrail | Every parsed name, title, company, institution, skill, bullet, email, phone and link is checked against the resume text. Anything not found is removed and listed on the review screen. |
| Master profile | Upload alone saves nothing. The user confirms the fields, and each save creates a new version. |
| Preferences | Roles, locations, work mode, min CTC, notice period, companies to skip. |
| Account deletion | One step: removes the user, every profile version, preferences and uploaded files. |

## Run it locally

You need JDK 21, Node 20+ and Docker.

```bash
# 1. Database
docker compose up -d

# 2. API on :8080 (runs migrations on start)
cd backend
./gradlew run

# 3. Web on :3000 (proxies /api/* to the API)
cd web
npm install
npm run dev
```

Open http://localhost:3000 and enter any email. In dev the sign-in link is logged by the API and shown on the sign-in page, so no email provider is needed.

To parse resumes with Claude, export `ANTHROPIC_API_KEY` before `./gradlew run`. All settings are in [`backend/.env.example`](backend/.env.example); the API reads them from environment variables.

## Tests

```bash
cd backend && ./gradlew test     # unit tests + API tests against Postgres via Testcontainers (needs Docker)
cd web && npm run typecheck
```

GitHub Actions (`.github/workflows/ci.yml`) runs both on every push.

## API

All routes except `/health` and `/auth/*` need the session cookie. In the browser they're under `/api/…`.

| Method | Path | Body → Response |
| --- | --- | --- |
| POST | `/auth/magic-link` | `{email}` → `{ok, devLink?}` |
| POST | `/auth/verify` | `{token}` → user, sets cookie |
| POST | `/auth/logout` | clears cookie |
| GET | `/me` | `{id, email, plan, hasProfile}` |
| DELETE | `/me` | deletes the account |
| POST | `/resume` | multipart `file` → `{resumeFile, parser, profile, removed}` (draft, not saved) |
| GET | `/profile` | latest version → `{version, profile, resumeFile, updatedAt}` |
| PUT | `/profile` | `{profile, resumeFileId?}` → new version |
| GET/PUT | `/preferences` | preferences object |

Validation errors return `400 {error, problems: [...]}`.

## Production notes

- Set `APP_ENV=prod`, a 32+ character `JWT_SECRET`, and `RESEND_API_KEY` + `MAIL_FROM`. The API refuses to start in prod without them.
- Serve the web app and API under one site (the Next.js rewrite does this), with `WEB_BASE_URL` set to the public web origin; cross-origin state-changing requests are rejected.
- Uploads go to `STORAGE_DIR` on local disk. `FileStorage` is the seam for moving to Cloudflare R2.
- `backend/Dockerfile` builds a fat jar image.

## Next: Phase 2 (job feed)

Ingest Greenhouse, Lever and Ashby boards nightly, apply the hard filters from preferences, then add embedding + LLM fit scores.
