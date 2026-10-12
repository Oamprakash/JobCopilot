# Apply Copilot

AI job-application copilot: finds well-matched jobs, tailors each application, and autofills forms, while the user always clicks Submit.

Built so far: **Phase 1 (Foundation)**, which covers sign-in, resume parsing, the master profile and preferences, and **Phase 2 (Job feed)**, which covers board ingestion, matching and the daily feed.

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

## What works in Phase 2

| Area | What it does |
| --- | --- |
| Job sources | Public board APIs only: Greenhouse (`boards-api.greenhouse.io`), Lever (`api.lever.co/v0/postings`), Ashby (`api.ashbyhq.com/posting-api`). No LinkedIn, Naukri or Indeed scraping. 11 verified seed boards with India openings ship in `V2__job_feed.sql`. |
| Ingestion | Every `INGEST_INTERVAL_HOURS` (default 24): fetch each active board, upsert jobs, close jobs that disappeared. A failed fetch leaves existing jobs open and records the error on the company. |
| Hard filters | Location (city aliases such as Bangalore/Bengaluru; remote jobs only if open to India or APAC), work mode, role (distinctive words of target roles, with synonyms like android→mobile), experience band from the JD, entry-level titles, companies to skip. |
| Shortlist | TF-IDF cosine between profile and JD; top 50 go on to scoring. |
| Fit score | Claude (`CLAUDE_SCORING_MODEL`, batches of 8) for up to 50 jobs per user per day; the rule-based scorer covers the rest, or everything when no API key is set. Claude's "missing skills" are kept only if the JD mentions them. |
| Feed | New / Saved / Skipped tabs, score, 3 reasons, missing skills, full JD, link to the job. Changing the profile or preferences clears unreviewed matches and rebuilds the feed on the next visit. |
| Admin | `ADMIN_EMAILS` can list boards, add one (synced straight away, so a wrong token shows its error), pause one, or run a sync. |

Adding a board: `POST /api/admin/companies {"name":"Acme","ats":"greenhouse","boardToken":"acme"}`. The token is the part of the board URL after `boards.greenhouse.io/`, `jobs.lever.co/` or `jobs.ashbyhq.com/`.

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
| GET | `/feed?status=new\|saved\|skipped` | `{jobs, lastRun, needsRefresh}` |
| POST | `/feed/refresh` | rebuilds the feed (throttled to once per 2 min unless inputs changed) |
| PUT | `/feed/{jobId}/status` | `{status}` |
| GET | `/jobs/{jobId}` | job with full description |
| GET/POST | `/admin/companies` | list / add a board (admins) |
| PUT | `/admin/companies/{id}/active` | `{active}` (admins) |
| POST | `/admin/ingest` | sync every active board now (admins) |

Validation errors return `400 {error, problems: [...]}`.

## Production notes

- Set `APP_ENV=prod`, a 32+ character `JWT_SECRET`, and `RESEND_API_KEY` + `MAIL_FROM`. The API refuses to start in prod without them.
- Serve the web app and API under one site (the Next.js rewrite does this), with `WEB_BASE_URL` set to the public web origin; cross-origin state-changing requests are rejected.
- Uploads go to `STORAGE_DIR` on local disk. `FileStorage` is the seam for moving to Cloudflare R2.
- `backend/Dockerfile` builds a fat jar image.

## Next: Phase 3 (Tailoring)

A tailored resume, cover letter and screening answers per job, each with a claim guardrail and a review screen.
