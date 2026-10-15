# Launch checklist

Moving from the free preview on Render to a public launch on your own domain. Do the steps in order; each ends with how to check it worked. "Your domain" below means the one you bought (`trulyapply.com`).

## 0. Rename the app (done: TrulyApply)

The app is named TrulyApply (`web/lib/brand.ts`, the API's default `APP_NAME`, `extension/manifest.json`), with `hello@trulyapply.com` as the contact address. To rename again, change those three places and set `APP_NAME` on Render.

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
1. Create a Neon project on the same Postgres version as Render's database (shown on its page; 18 at the time of writing), region **AWS Asia Pacific (Singapore)** (same region as the app). On the project dashboard, **Connect** → copy the connection string (it starts `postgresql://` and ends `sslmode=require…`).
2. In Render → the database → **Connect**, copy the **External** connection URL. Save the service's current `DATABASE_URL` value in your notes too, for switching back.
3. Copy the data with Docker, in PowerShell, from any folder (a quiet hour is best, so nothing is written mid-copy):
   ```powershell
   docker run --rm -v ${PWD}:/w postgres:18 pg_dump -Fc --no-owner --no-acl -d "RENDER_EXTERNAL_URL" -f /w/jobcopilot.dump
   docker run --rm -v ${PWD}:/w postgres:18 pg_restore --no-owner --no-acl -d "NEON_URL" /w/jobcopilot.dump
   ```
   Keep the quotes around each URL.
4. Render → service → **Environment**: replace `DATABASE_URL` with the Neon string and save. The service redeploys.
5. `render.yaml` already has `DATABASE_URL` as `sync: false` (done when this app moved to Neon), so a Blueprint sync keeps the value you set.
6. Delete `jobcopilot.dump` from your computer afterwards: it contains users' data. Leave the old Render database alone for a few days as a fallback.

**Check:** sign in on the live site; your profile, tracker and admin dashboard data are all there.

## 5. Resume files (nothing to set up)

Uploaded resumes are read into the profile and not kept, so no file storage is needed. If you set up R2 earlier, you can remove `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` from Render, empty and delete the bucket, and delete its API token. (The app still supports R2 should a feature ever need file storage.)

## 6. Always-on (optional)

Free services sleep after 15 idle minutes, so the first visit takes about a minute. For real users, move the service to Render's smallest paid instance (Settings → Instance type). It also stops scheduled syncs and follow-up reminders from waiting for someone to visit.

## 7. Chrome Web Store

Follow [chrome-web-store.md](chrome-web-store.md). It needs the live `https://yourdomain.com/privacy` page from step 2.

## 8. Before you invite the public

- [ ] Read `/privacy` and `/terms` end to end. They're written to match how the app works, but they aren't legal advice; have a lawyer look before taking payments or scaling up.
- [ ] Ask Adzuna about a commercial licence (their free key is for development and personal use beyond a 14-day trial), or remove `ADZUNA_APP_ID` / `ADZUNA_APP_KEY`.
- [ ] Trademark search for the name at IP India (ipindiaonline.gov.in).
- [ ] Remove test accounts from the admin user list if you don't want them in your numbers.
