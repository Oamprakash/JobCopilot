# TrulyApply

AI job-application copilot: finds well-matched jobs, tailors each application, and autofills forms, while the user always clicks Submit.

Built so far: **Phase 1 (Foundation)**: sign-in, resume parsing, the master profile and preferences. **Phase 2 (Job feed)**: board ingestion, matching and the daily feed. **Phase 3 (Tailoring)**: a tailored resume, cover letter and screening answers per job, with a claim guardrail and a review screen. **Phase 4 (Extension and tracker)**: a Chrome extension that fills application forms and saves jobs from any page, and an application tracker with follow-up reminders. **Phase 5 (Engagement)**: a daily match email and interview prep.

```
backend/   Kotlin + Ktor API, Postgres (Flyway), resume parsing (PDFBox, POI, Claude API)
web/       Next.js 14 app: sign-in, onboarding, profile, feed, review screen, tracker, admin
extension/ Chrome extension (Manifest V3): autofill, Save job, mark applied
```

## What works in Phase 1

| Area | What it does |
| --- | --- |
| Magic-link sign-in | Email → one-time link (15 min, single use, 5 per hour per email). Session is a 30-day JWT in an HttpOnly, SameSite=Lax cookie. Only token hashes are stored. |
| Resume upload | PDF or DOCX up to 5 MB, detected by file bytes. Scanned PDFs, password-protected PDFs and other formats get a clear error. |
| Parsing | Claude (forced tool call → profile JSON) when `ANTHROPIC_API_KEY` is set; a rule-based parser otherwise, or if the API call fails. |
| "Never invent" guardrail | Every parsed name, title, company, institution, skill, bullet, email, phone and link is checked against the resume text. Anything not found is removed and listed on the review screen. |
| Master profile | Upload alone saves nothing. The user confirms the fields, and each save creates a new version. |
| Preferences | Roles, locations, work mode, min CTC, notice period, companies to skip. Roles and locations suggest values while you type (↑/↓, Enter, Escape), with how many open jobs each would match; cities also match old names such as Bangalore or Gurgaon. |
| Account deletion | One step: removes the user, every profile version, preferences and uploaded files. |

## What works in Phase 2

| Area | What it does |
| --- | --- |
| Job sources | Public board APIs only: Greenhouse (`boards-api.greenhouse.io`), Lever (`api.lever.co/v0/postings`), Ashby (`api.ashbyhq.com/posting-api`), Workable (`apply.workable.com/api/v1/widget`) and SmartRecruiters (`api.smartrecruiters.com/v1/companies/{id}/postings`). No LinkedIn, Naukri or Indeed scraping. Seed boards with India engineering openings ship in migrations V2–V4. **Adzuna** (optional, `ADZUNA_APP_ID` + `ADZUNA_APP_KEY`) is a licensed job-search API that aggregates employer career sites, which brings in companies whose own sites have no public API (most MAANG, Fortune 500 and IT services firms, which use Workday or their own portals). SmartRecruiters boards are seeded **paused**: that API host disallows automated access in robots.txt and its docs describe API-key access, so confirm their terms (or get a Posting API key) before activating them. `tools/probe_boards.py` (run by the *Probe job boards* workflow) checks a candidate list and writes the results to the `probe-results` branch. |
| Ingestion | Every `INGEST_INTERVAL_HOURS` (default 24): fetch each active board, upsert jobs, close jobs that disappeared. A failed fetch leaves existing jobs open and records the error on the company. |
| Adzuna searches | Each user's target roles become their distinctive words ("Senior Android Engineer" → `android`), searched per preferred city in that city's country (Singapore searches Adzuna Singapore; a country choice searches the whole country). Adzuna doesn't cover Malaysia or the UAE, so jobs there come from the company boards only; identical searches are shared across users. Each search runs at most once every 20 hours, both in the daily sync and on the user's first refresh after changing preferences. Calls are counted per UTC day in the database (`ADZUNA_DAILY_CALLS`, default 60, inside the free key's 250/day and 2,500/month) and spaced to stay under 25 a minute. Adzuna jobs show their real employer, carry "Jobs by Adzuna" attribution and link through Adzuna. Adzuna gives only a summary of each description, which the feed and review screen point out. A job also on the employer's own board is shown once, from the board. Jobs not seen for 21 days close. Licence: the free key covers development and personal use; commercial use past a 14-day trial needs an agreement with Adzuna. Their terms also ask for the Adzuna logo (≥116×23 px) on listings: add it from their press page. |
| Continue with Google | Set `GOOGLE_CLIENT_ID` (a Google Cloud OAuth client of type *Web application*, with `https://trulyapply.com` as an authorised JavaScript origin) and the sign-in page shows Google's button. The browser gets an ID token from Google Identity Services; `POST /api/auth/google` checks it with Google's tokeninfo endpoint (issuer, audience = our client ID, expiry, verified email) and then applies the same invite rules and session as email links. Only the email is kept. |
| Fit check (home page) | `POST /api/try/fit-check` (multipart: `file`, `description`, optional `title`), no sign-in: reads the resume, scores it against the pasted job and returns matched and missing skills plus up to 3 of the visitor's lines, reworded by Claude and checked by the guard, or reordered by the rule-based tailor. Nothing is stored. Caps: `FIT_CHECK_DAILY` checks a day in all (default 300), `FIT_CHECK_AI_DAILY` of them with Claude (default 40, 0 = never), 5 an hour per visitor address. The parsed profile goes back to the browser so onboarding can skip the upload (kept up to 3 hours). |
| Himalayas searches | [Himalayas' free remote-jobs API](https://himalayas.app/docs/remote-jobs-api) (no key) adds remote jobs. Users pick the countries they can work from under Preferences (**Remote jobs: countries you can work from**, 20 countries; none chosen means India); each search asks for jobs open to one of those countries, worldwide ones included, and the feed hides remote jobs open only to other countries (the job's listed countries and regions such as "North America" or "EMEA" are matched). Only users who'd take remote work (work mode Any or Remote, or "Remote" among their places) trigger a search: one per role keyword and country (up to 3 countries), shared across users, at most once every 20 hours (Himalayas refreshes its data daily). Calls are counted per UTC day (`HIMALAYAS_DAILY_CALLS`, default 120). Jobs show their real employer with the full description, carry "Remote job via Himalayas" linked to himalayas.app, and link to the listing on Himalayas, as its API terms ask. It has its own switch on the Admin page and is on after migration V17. Its site terms separately limit commercial reuse of site content; confirm with hi@himalayas.app before charging users. |
| Hard filters | Location (city aliases such as Bangalore/Bengaluru; hubs abroad in Singapore, the UAE, Malaysia, Germany, the Netherlands, the UK, Ireland, Canada, Australia, New Zealand and Japan; whole countries such as Germany or UAE match any city in them; remote jobs only if open to India or APAC), work mode, role (distinctive words of target roles, with synonyms like android→mobile), experience band from the JD, entry-level titles, companies to skip. |
| Visa sponsorship | Each job's description is read for what it says about visa sponsorship (offered / ruled out / not stated) and relocation support, shown as badges on the card. With **I'll need visa sponsorship for jobs abroad** ticked, jobs outside India whose description rules sponsorship out are filtered ("not stated" stays in, since most descriptions don't say). Preferences include a guide to countries with employer-sponsored routes (EU Blue Card, Highly Skilled Migrant, Skilled Worker, Critical Skills, Global Talent Stream, Skills in Demand, AEWV, Employment Pass, UAE work visa) with one-tap Add and links to the official pages; it states no salary figures because they change yearly. |
| Shortlist | TF-IDF cosine between profile and JD; top 50 go on to scoring. |
| Fit score | Claude (`CLAUDE_SCORING_MODEL`, batches of 8) for up to 50 jobs per user per day; the rule-based scorer covers the rest, or everything when no API key is set. Claude's "missing skills" are kept only if the JD mentions them. |
| Feed | An empty feed says why, e.g. "290 were different roles, 15 were outside your locations". **Widen your search** suggests the single changes (add a city, add a role family, include remote) that would let the most filtered-out jobs through, counting only jobs that mention at least two of the user's skills; one tap updates preferences and rebuilds the feed. New / Saved / Skipped tabs; New hides matches scoring below 40 unless **Show weaker matches** is ticked (it says how many are hidden), while Saved and Skipped show every score. Score, 3 reasons, missing skills, full JD, link to the job. Changing the profile or preferences clears unreviewed matches and rebuilds the feed on the next visit. |
| Admin | `ADMIN_EMAILS` can list boards, add one (synced straight away, so a wrong token shows its error), pause one, or run a sync. |
| Admin dashboard | Admins sign in like anyone else (magic link) and get an **Admin** tab: users total / new / active (7 and 30 days), active users per day, how far users get (profile → refresh → tailor → approve → click to apply), activity counts, most searched roles and locations, and companies users clicked through to apply to. The user list (search by email or name) shows each user's current search and totals; a user's page shows their timeline: sign-ins, every saved search, feed refreshes, jobs opened / saved / skipped, applications tailored and approved, PDFs downloaded and clicks to apply. Events are stored in `events` (V11); the two link clicks are reported by the browser through `POST /events`, everything else is recorded server-side. Opening a user's page is itself recorded against the admin. Deleting an account deletes its events; events older than `EVENT_RETENTION_DAYS` (default 180) are deleted daily. The sign-in page tells users their activity is recorded. |

Adding a board: `POST /api/admin/companies {"name":"Acme","ats":"greenhouse","boardToken":"acme"}`. The token is the part of the board URL after `boards.greenhouse.io/`, `jobs.lever.co/`, `jobs.ashbyhq.com/` or `apply.workable.com/`; for SmartRecruiters it is the company id in `jobs.smartrecruiters.com/{id}` (case matters, e.g. `BoschGroup`). To turn a paused board on: `PUT /api/admin/companies/{id}/active {"active": true}` (ids from `GET /api/admin/companies`). Adzuna and Himalayas have switches on the Admin page (Job sources); new matches and daily emails only include jobs from switched-on sources.

## What works in Phase 3

| Area | What it does |
| --- | --- |
| Drafting | **Tailor and apply** on a feed card opens the review screen and drafts the application. Claude (`CLAUDE_MODEL`) drafts it when `ANTHROPIC_API_KEY` is set; otherwise, or if the call fails, a rule-based tailor reorders skills and bullets toward the job and builds the letter and answers from profile facts. |
| Claim guardrail | `TailorGuard` decides what goes out, whoever drafted it. Roles, titles, companies and dates always come from the profile, and every role stays. A bullet may be reordered or reworded, but one that adds a number or skill the profile lacks, or drifts from its source, reverts to the original. Skills must be the user's own. Sentences in the summary, cover letter or answers with unbacked numbers or skills are dropped. Everything removed is listed on the review screen. |
| Sensitive questions | Pay/CTC, visa, sponsorship and personal or legal questions are never answered for the user. Notice period comes only from preferences. |
| Review screen | Resume diff (moved up / reworded with the original struck through / unchanged), editable bullets, summary, cover letter and answers, copy buttons, JD keywords covered and missing. The user's own edits are saved but flagged if they claim something the profile doesn't show. |
| Full description | When a job's source gave only a summary (Adzuna), the review screen offers **Paste the full job description**. The draft, guardrail and keyword coverage are rebuilt from the pasted text (200–30,000 characters), stored with that application only; regenerating keeps it, and **Use the summary instead** goes back. Re-tailoring doesn't use another free-plan application. |
| Approve + PDF | Approving locks the version and unlocks an ATS-friendly resume PDF and a link to the employer's application page. Editing after approval sends it back to draft. |
| Free plan | `FREE_TAILOR_LIMIT` (default 5) new tailored applications per calendar month; regenerating one doesn't count again. Pro is unlimited (`users.plan = 'pro'`; billing comes later). |

## What works in Phase 4

| Area | What it does |
| --- | --- |
| Chrome extension | `extension/` (Manifest V3). **Autofill this application** fills empty fields on Greenhouse, Lever, Ashby and Workday forms (also Greenhouse forms embedded on company sites): name, email, phone, location, LinkedIn / GitHub / website, current company and title, the cover letter, the tailored answers to matching questions, and the tailored resume PDF in the resume upload. Fields you already typed in are left alone, sensitive questions (pay, visa, notice, demographics) are never filled, filled fields are outlined, and it never clicks Submit. The application is matched to the page by URL; otherwise you pick one of your recent applications, or fill just your details. |
| Save job | **Save this job** on any job page reads the posting (schema.org `JobPosting`, then page tags) and adds it to the tracker as Saved. A page for a job the app already knows reuses that job; others are kept as saved jobs that never enter feeds. |
| Tracker | `/tracker`: every saved or applied job with its stage (Saved, Applied, Interviewing, Offer, Rejected, Withdrawn), applied date, follow-up date (+3 days / +1 week / done) and notes, filterable by stage and "Follow up". Add jobs from anywhere by hand. **I submitted it** (review screen or extension) marks a job applied and sets a follow-up a week out. |
| Follow-up reminders | Hourly, each user gets one email listing the tracked jobs whose follow-up date has passed (once per date), linking to the tracker. Without `RESEND_API_KEY` the email is logged. |
| Extension sign-in | Profile → **Browser extension** gives a one-time 8-character code (10 minutes). The extension exchanges it for its own token (only a hash is stored) and asks permission for just your app's address. **Disconnect everywhere** revokes every extension token. |

## What works in Phase 5

| Area | What it does |
| --- | --- |
| Daily match email | Each morning (`DIGEST_HOUR`, default 8, in `DIGEST_TIMEZONE`, default Asia/Kolkata) every user with a profile gets up to 5 of their best new matches (score 40+) scored since their last email, with a count of the rest and links to review each. No new matches, no email. On by default; off under Preferences or with the one-click unsubscribe link (signed per user; `List-Unsubscribe` headers for mail apps). Checked hourly, so a restart never sends twice. |
| Feedback | **Feedback** in the header on every signed-in page: pick a kind (something's broken, an idea, something I like, other), write a message, send. The page it came from is attached (our own path only). Admin → **Feedback** lists it newest first with the sender (linked to their activity), filterable by New, with Mark done. Stored in `feedback` (V14), deleted with the account. |
| Interview prep | Tracker → **Interview prep** on an applied or interviewing job: up to 10 likely questions (about you, the job's skills, gaps, leadership, the company), each with why it's asked, a tip, and talking points that are your own resume bullets, word for word. Skills the job asks for that your profile lacks are listed so you can prepare an honest answer. Uses Claude when `ANTHROPIC_API_KEY` is set (rule-based otherwise or on failure); whatever Claude writes passes the same claim guard as tailoring. 10 a day per user. |

### Install the extension

1. Get the code: download the repository (or `git pull`) so you have the `extension` folder.
2. In Chrome, open `chrome://extensions`, turn on **Developer mode** (top right), click **Load unpacked** and choose the `extension` folder.
3. Pin TrulyApply from the puzzle-piece menu.
4. In the app, open Profile → **Browser extension** → **Connect the extension**. In the extension popup, enter the app's address (e.g. `https://jobcopilot.onrender.com` or `http://localhost:3000`) and the code.

On an application form, click the extension and **Autofill this application**, review every field, then click the site's Submit yourself. After a `git pull`, click the reload icon on the extension's card in `chrome://extensions`.

## Run it locally

You need JDK 21, Node 20+ and Docker.

```bash
# 1. Database
docker compose up -d

# 2. API on :8080 (runs migrations on start). Optional: cp backend/.env.example backend/.env and fill in keys.
cd backend
./run.sh          # macOS/Linux: loads .env, then ./gradlew run
.\run.ps1         # Windows PowerShell: loads .env, then gradlew.bat run

# 3. Web on :3000 (proxies /api/* to the API)
cd web
npm install
npm run dev
```

`./gradlew run` stays at about 83% for as long as the API is running; that's normal, because the run task only finishes when the server stops. The API on :8080 has no pages of its own, so open the app at http://localhost:3000 and enter any email. In dev the sign-in link is logged by the API and shown on the sign-in page, so no email provider is needed.

All settings are in [`backend/.env.example`](backend/.env.example). The API reads environment variables, not the file itself, so start it with `run.sh` / `run.ps1`, which load `backend/.env` (git-ignored) first; empty values keep the defaults. To set one by hand instead: `export ANTHROPIC_API_KEY=...` (macOS/Linux) or `$env:ANTHROPIC_API_KEY = "..."` (PowerShell), in the same terminal, before `./gradlew run`. If PowerShell refuses to run the script, run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

## Launch

- [docs/launch.md](docs/launch.md): your own domain, email sign-in (Resend), production mode, moving the database before the free one expires and a pre-launch checklist.
- [docs/chrome-web-store.md](docs/chrome-web-store.md): publishing the extension (listing text, permission justifications, data disclosures).
- The app's name lives in `web/lib/brand.ts`, `APP_NAME` and `extension/manifest.json`. `/privacy` and `/terms` read it from there.

## Deploy to Render (free, always online)

`render.yaml` is a Render Blueprint: one free Docker web service in Singapore that runs the API and the web app together (`deploy/render.Dockerfile`), plus a free Postgres database. CI builds this image and smoke-tests it at 512 MB on every push.

1. Sign in at [render.com](https://render.com) with GitHub.
2. **New → Blueprint**, pick this repository. Render reads `render.yaml`.
3. Fill in the prompted values: `ADMIN_EMAILS` (you), `INVITE_EMAILS` (who may sign in; empty lets anyone in), and optionally `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` / `ANTHROPIC_API_KEY`. Click **Apply**. The first build takes about 10 minutes.
4. Open the service URL (`https://jobcopilot….onrender.com`). Invited users see their sign-in link on screen. Admin links are never shown on screen: find yours under the service's **Logs** ("Magic link for …").

Free-tier limits: the service sleeps after 15 idle minutes (the next visit takes about a minute), the free database expires 30 days after creation. Every push to `main` redeploys. For real users: a paid instance and database, and an email service (`RESEND_API_KEY`, then `APP_ENV=prod`).

## Share a preview (Cloudflare Tunnel)

To let someone try the app running on your computer, without deploying it:

1. Install `cloudflared` (`winget install --id Cloudflare.cloudflared` on Windows, `brew install cloudflared` on macOS).
2. Build and start the web app for speed: `cd web && npm run build && npm start` (port 3000).
3. In another terminal: `cloudflared tunnel --url http://localhost:3000`. It prints a `https://….trycloudflare.com` link.
4. In `backend/.env` set `WEB_BASE_URL` to that link, `PUBLIC_PREVIEW=true` and `INVITE_EMAILS=friend@example.com`, then restart the API with `run.ps1` / `run.sh`.
5. Send the link. Your friend enters their email and the sign-in link appears on screen (dev mode, no email service needed). Anyone not on `INVITE_EMAILS` is refused, and admin sign-in links are printed only in the API window.

Only the web app goes through the tunnel; it forwards `/api` to the API on your computer. The link works while your computer, the API, the web app and `cloudflared` are running. A quick tunnel gets a new link each time, so update `WEB_BASE_URL` when it changes; a named tunnel on your own domain keeps one link. Put `WEB_BASE_URL` back to `http://localhost:3000` and `PUBLIC_PREVIEW=false` when you're done.

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
| GET | `/autocomplete` | `{locations, roles}`: `[{value, count, aliases}]`, counts from open jobs (cached 10 min) |
| GET | `/feed?status=new\|saved\|skipped&weak=true` | `{jobs, lastRun, needsRefresh, minScore, hiddenWeak}`; `weak=true` includes New matches below `minScore` |
| POST | `/feed/refresh?weak=true` | rebuilds the feed (throttled to once per 2 min unless inputs changed) |
| PUT | `/feed/{jobId}/status` | `{status}` |
| GET | `/jobs/{jobId}` | job with full description |
| GET/POST | `/admin/companies` | list / add a board (admins) |
| GET | `/admin/overview?days=7\|30\|90` | dashboard totals, daily activity, funnel, top searches (admins) |
| GET | `/admin/users?q=&offset=&limit=` | users with their search and totals (admins) |
| GET | `/admin/users/{id}` | one user's preferences, applications and activity timeline (admins; audited) |
| GET | `/tracker` | `{items, counts, dueCount}` |
| POST | `/tracker` | `{jobId, stage}`: track a known job (`applied` sets a follow-up in 7 days) |
| POST | `/tracker/manual` | `{title, company, url, location?, stage?}` |
| PUT/DELETE | `/tracker/{jobId}` | `{stage?, followUpAt?, clearFollowUp?, notes?}` / remove |
| GET/POST | `/tracker/{jobId}/prep` | saved interview prep (404 if none) / make a new one |
| GET/POST | `/digest/unsubscribe?u=&t=` | one-click unsubscribe from the daily match email (no sign-in; signed link) |
| GET/POST/DELETE | `/extension`, `/extension/pair-code` | extension status, a pairing code, disconnect all |
| | `/ext/*` | the extension's API (bearer token): `pair`, `me`, `autofill?url=&jobId=`, `applications/{jobId}/resume.pdf`, `save`, `applied` |
| POST | `/events` | `{type: "job_opened"\|"apply_clicked", jobId}`: link clicks the browser reports |
| PUT | `/admin/companies/{id}/active` | `{active}` (admins) |
| POST | `/feedback` | `{kind: idea\|problem\|praise\|other, message, page?}` from the header's Feedback button (20 a day per user) |
| GET/PUT | `/admin/feedback?status=new`, `/admin/feedback/{id}` | feedback list with new count / `{status: new\|done}` (admins) |
| GET/PUT | `/admin/sources/{adzuna\|himalayas}` | Aggregator status / `{enabled}`: the Admin page's switches. Off: no calls to that source, and its jobs leave New matches and daily emails (admins) |
| POST | `/admin/ingest` | sync every active board now (admins) |
| POST | `/applications/{jobId}/tailor` | `{questions?, jobDescription?}` → draft (creates or regenerates); `jobDescription` replaces the job's text for this application, `""` clears it |
| GET/PUT | `/applications/{jobId}` | the application / save the user's edits `{application}` |
| POST | `/applications/{jobId}/approve` | lock in the version |
| GET | `/applications/{jobId}/resume.pdf` | tailored resume as PDF |
| GET | `/usage` | `{plan, tailoredThisMonth, limit}` |

Validation errors return `400 {error, problems: [...]}`.

## Production notes

- Set `APP_ENV=prod`, a 32+ character `JWT_SECRET`, and `RESEND_API_KEY` + `MAIL_FROM`. The API refuses to start in prod without them.
- Serve the web app and API under one site (the Next.js rewrite does this), with `WEB_BASE_URL` set to the public web origin; cross-origin state-changing requests are rejected.
- Uploaded resumes are read and discarded: only the file name is kept. `FileStorage` (local disk or R2) is still there for features that need files.
- `backend/Dockerfile` builds a fat jar image.

