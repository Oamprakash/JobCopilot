# Launch checklist

Moving from the free preview on Render to a public launch on your own domain. Do the steps in order; each ends with how to check it worked. "Your domain" below means the one you bought (e.g. `trulyapply.com`).

## 0. Rename the app (5 minutes)

Change the name in three places and push:

- `web/lib/brand.ts`: `name`, `operator` (you or your business, as it should appear in the privacy policy), `contactEmail` (e.g. `privacy@yourdomain.com`), `jurisdiction`.
- Render → service → Environment: `APP_NAME` (used in emails).
- `extension/manifest.json`: `name` (the popup and messages read it from there).

**Check:** the header, sign-in page, `/privacy` and `/terms` show the new name and contact email.

## 1. Domain (Cloudflare Registrar)

Buy the domain at Cloudflare (Domain Registration → Register Domains). DNS is then managed in the same dashboard, which the next steps use.

## 2. Point the domain at the app (Render)

1. Render → your service → **Settings → Custom Domains → Add**: add `yourdomain.com` (and `www.yourdomain.com` if you want it).
2. Render shows the DNS record to create for each. Add exactly those in Cloudflare → your domain → **DNS**, with the proxy **off** (grey cloud, "DNS only") so Render can issue the HTTPS certificate.
3. Wait for Render to show the domain as verified with a certificate.
4. Render → Environment: set `WEB_BASE_URL=https://yourdomain.com`. **This is required**: sign-in links and the request-origin check use it. Without it they keep using the `.onrender.com` address.

**Check:** `https://yourdomain.com` opens the app, and a sign-in link's address starts with your domain.

## 3. Email sign-in (Resend)

1. Create a Resend account and add your domain under **Domains**. Resend lists the DNS records to add (for sending and for DKIM). Add each one in Cloudflare DNS exactly as shown, again **DNS only**.
2. Wait until Resend shows the domain as **Verified**.
3. Create an API key (sending access) and set in Render → Environment:
   - `RESEND_API_KEY=re_…`
   - `MAIL_FROM=YourApp <login@yourdomain.com>`
4. Switch to production mode: set `APP_ENV=prod`. In prod the API refuses to start without `RESEND_API_KEY`, and sign-in links are only emailed, never shown on screen.
5. Decide who can sign up:
   - **Invite-only beta:** keep `PUBLIC_PREVIEW=true` and list people in `INVITE_EMAILS`.
   - **Open to everyone:** set `PUBLIC_PREVIEW=false`.

**Check:** request a sign-in link with your own email. It arrives from `login@yourdomain.com`, isn't in spam, and signs you in. The startup log line no longer says "Public preview" if you opened it up.

## 4. Move the database before the free one expires (by about 12 November)

Render's free Postgres expires 30 days after creation. Choose one:

**A. Upgrade on Render (simplest):** Render → the database → upgrade to a paid plan. Nothing else changes.

**B. Move to Neon (has a free tier):**
1. Create a Neon project in **AWS Asia Pacific (Singapore)**, the same region as the app, and copy its connection string (it includes `sslmode=require`).
2. In Render → the database → **Connect**, copy the **External** connection URL.
3. Copy the data with Docker (installed for local development), in PowerShell:
   ```powershell
   docker run --rm -v ${PWD}:/w postgres:18 pg_dump -Fc --no-owner --no-acl -d "RENDER_EXTERNAL_URL" -f /w/jobcopilot.dump
   docker run --rm -v ${PWD}:/w postgres:18 pg_restore --no-owner --no-acl -d "NEON_URL" /w/jobcopilot.dump
   ```
   Use a `postgres` image version at least as new as Render's database (shown on its page).
4. Render → service → Environment: replace `DATABASE_URL` with the Neon URL. In `render.yaml`, change the `DATABASE_URL` entry from `fromDatabase` to `sync: false` so a Blueprint sync doesn't point it back at Render.
5. Delete `jobcopilot.dump` from your computer afterwards: it contains users' data.

**Check:** sign in on the live site; your profile, tracker and admin dashboard data are all there.

## 5. Keep uploaded resumes (Cloudflare R2)

Optional but recommended: the original resume files otherwise disappear when the service restarts (profiles themselves are in the database).

1. Cloudflare → **R2** → create a bucket (e.g. `jobcopilot-uploads`). Leave public access **off**.
2. R2 → **Manage API tokens** → create a token with **Object Read & Write** on that bucket only.
3. Render → Environment: `R2_ACCOUNT_ID` (your Cloudflare account ID), `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`. Set all four or none: the API refuses to start with only some.

**Check:** upload a resume in the app; the file appears in the bucket.

## 6. Always-on (optional)

Free services sleep after 15 idle minutes, so the first visit takes about a minute. For real users, move the service to Render's smallest paid instance (Settings → Instance type). It also stops scheduled syncs and follow-up reminders from waiting for someone to visit.

## 7. Chrome Web Store

Follow [chrome-web-store.md](chrome-web-store.md). It needs the live `https://yourdomain.com/privacy` page from step 2.

## 8. Before you invite the public

- [ ] Read `/privacy` and `/terms` end to end. They're written to match how the app works, but they aren't legal advice; have a lawyer look before taking payments or scaling up.
- [ ] Ask Adzuna about a commercial licence (their free key is for development and personal use beyond a 14-day trial), or remove `ADZUNA_APP_ID` / `ADZUNA_APP_KEY`.
- [ ] Trademark search for the name at IP India (ipindiaonline.gov.in).
- [ ] Remove test accounts from the admin user list if you don't want them in your numbers.
