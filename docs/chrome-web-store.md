# Publishing the extension on the Chrome Web Store

Everything Google asks for, ready to paste. The privacy policy URL is `https://trulyapply.com/privacy`.

## 1. One-time setup

1. Sign in at the [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole) with the Google account that will own the listing.
2. Pay the one-time US$5 registration fee and verify your contact email.
3. Have your live privacy policy URL ready: `https://trulyapply.com/privacy`.

## 2. The package

Build a zip of the extension without its tests:

- GitHub: every CI run uploads **extension-zip** (Actions → the latest run → Artifacts).
- Windows, from the repository folder: `.\extension\pack.ps1` → `extension.zip`
- macOS/Linux: `cd extension && zip -r ../extension.zip . -x 'test/*'`

Upload it under **Package → Upload new package**. Bump `version` in `manifest.json` for every update you submit.

## 3. Store listing

**Name:** TrulyApply

**Summary (up to 132 characters):**
Fill job applications from your reviewed, tailored application and save jobs to your tracker. You always click Submit.

**Description:**
TrulyApply's extension saves you retyping the same details into every job application, without sending anything on your behalf.

• Autofill: on Greenhouse, Lever, Ashby and Workday application forms, fill your name, contact details, links, current role, cover letter and the answers you approved in TrulyApply, and attach your tailored resume.
• Honest by design: only fields you've left empty are filled, from facts in your own profile. Pay, visa and demographic questions are always left to you, and every filled field is highlighted so you can check it.
• You stay in control: the extension never clicks Submit. You review the form and submit it yourself.
• Save any job: one click adds the job you're looking at to your TrulyApply tracker, with follow-up reminders.

The extension only runs when you click it, on the tab you're on. It needs a free TrulyApply account; connect it with a one-time code from your profile.

**Category:** Workflow & Planning (or Productivity)
**Language:** English

**Images** (ready in `docs/store-assets/`): icon `extension/icons/128.png`; screenshots `store-1-autofill.png` and `store-2-save.png` (1280×800); small promo tile `store-tile-440x280.png`. The forms and companies in the screenshots are made up.

## 4. Privacy practices tab

**Single purpose:**
Helps the user complete job applications they have prepared in TrulyApply: fills the application form on the current page from the user's own profile and approved application, and saves the current job posting to the user's tracker.

**Permission justifications:**

| Permission | Justification |
| --- | --- |
| `activeTab` | Fill the application form, or read the job posting, on the tab the user is on, only after they click the extension. |
| `scripting` | Inject the form-filling and job-reading scripts into that tab when the user clicks a button in the popup. |
| `storage` | Keep the app's address and the sign-in token that connects the extension to the user's own account. |
| Host permission: `trulyapply.com` | Reach the user's own TrulyApply account: fetch their profile and approved application, and save jobs to their tracker. |
| Host permissions: `boards.greenhouse.io`, `job-boards.greenhouse.io`, `jobs.lever.co`, `jobs.ashbyhq.com`, `*.myworkdayjobs.com` | Application forms on these hiring platforms are often embedded in an employer's careers page; the extension needs access to fill a form inside that embedded frame when the user clicks Autofill. |
| Optional host permissions (`http://localhost/*`, `http://127.0.0.1/*`) | Only for developers running a local copy of the app; requested when they connect to it. Never used with the live service. |

**Remote code:** No, I am not using remote code. (All scripts are in the package; the API returns data only.)

**Data usage, what the extension handles:**
- ☑ **Personally identifiable information**: the user's name, email, phone and links from their own profile, written into the form they choose to fill.
- ☑ **Authentication information**: the token that connects the extension to the user's account.
- ☑ **Website content**: the job posting on the current page when the user clicks "Save this job", and the page's address to find the matching application.
- ☐ Health, financial, personal communications, location, web history, user activity: not collected. (The page address is sent only for the page the user clicked the extension on, never a list of pages visited.)

**Certifications:** tick all three: no selling or transferring data outside the allowed uses, no use unrelated to the single purpose, no use for creditworthiness or lending.

These match the [privacy policy](../web/app/privacy/page.tsx): the extension runs only on click, never submits, and activity records note only that it was used (no pages or page content), so people never read data the extension collects.

## 5. Review

Submit for review. First reviews usually take a few days; extensions with broad host permissions take longer, which is why this one asks only for trulyapply.com and the job platforms it fills. After approval, share the store link instead of "Load unpacked".
