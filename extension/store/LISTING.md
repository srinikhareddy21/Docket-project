# Chrome Web Store listing — DOCKET — Save job

Use these values when submitting at https://chrome.google.com/webstore/devconsole

## Basic information

| Field | Value |
|--------|--------|
| **Name** | DOCKET — Save job |
| **Summary** (132 chars max) | Save the job you are viewing to your DOCKET account. Review every field before it is saved. |
| **Category** | Productivity |
| **Language** | English |

## Detailed description

```
DOCKET — Save job helps you capture job postings into your DOCKET job-application tracker without retyping details.

How it works
1. Sign in to your DOCKET account on the web.
2. Create a personal extension token under Settings → Browser extension.
3. Install this extension and open its settings. Enter your DOCKET server address (HTTPS) and paste the token. Test the connection.
4. On a job page, click the extension icon. When the page publishes structured job data (for example schema.org JobPosting), fields such as company, title, location, and description may be filled in. LinkedIn Jobs and Google Careers are supported with best-effort page reading; other sites may require manual entry.
5. Review and edit every field. Nothing is sent until you click “Save to DOCKET”.
6. The extension can also warn you about possible duplicate applications already in your account.

Privacy and security
• Job data is sent only to your configured DOCKET server, only after you click Save.
• The extension token can only create applications and check for duplicates. It cannot read your full history, change your password, or access other users’ data.
• Tokens are stored as hashes on the server and can be revoked at any time in DOCKET Settings.
• Permissions are limited to the active tab (when you open the extension), scripting to read that tab, storage for your settings, and host access only to the DOCKET server you authorize.

This extension requires an existing DOCKET account and a reachable DOCKET deployment. It does not provide a standalone job board.
```

## Permission justifications (store form)

| Permission | Justification |
|------------|----------------|
| **activeTab** | Read the job page the user is viewing only when they open the extension. |
| **scripting** | Inject a read-only extractor into the active tab to collect published job fields. |
| **storage** | Store the user’s DOCKET server URL and extension token on this device. |
| **Host permission (optional)** | Contact only the DOCKET API origin the user configures (HTTPS production site or localhost for development). |

## Privacy practices (Chrome Web Store)

- **Single purpose:** Save reviewed job details to the user’s DOCKET account.
- **Data used:** Job fields the user confirms (company, title, location, link, description, etc.) and the extension token for authentication.
- **Data shared:** Sent to the user’s own DOCKET backend (operator-controlled server), not sold.
- **Remote code:** No. All scripts are packaged in the extension.
- **Certifications:** Not certified by a third party (unless you obtain one later).

## Support

| Field | Suggested value |
|--------|------------------|
| **Homepage** | Your public DOCKET site URL (HTTPS) |
| **Support email** | Your support address |
| **Privacy policy URL** | `https://YOUR_SITE/legal/privacy` (complete the draft Legal page first) |

## Reviewer testing instructions

```
1. Open the DOCKET web app at [YOUR_PRODUCTION_HTTPS_URL] and register a test account (or use the credentials provided in the reviewer note).
2. Go to Settings → Browser extension → Create token. Copy the token (shown once).
3. Install this extension. Open extension options.
4. Server address: [YOUR_PRODUCTION_HTTPS_URL] (same origin as the website).
5. Paste the token → Save and test connection. You should see “Connected as …”.
6. Open a public job posting (e.g. a LinkedIn /jobs/view/… page or a page with schema.org JobPosting JSON-LD).
7. Click the extension icon, review fields, click Save to DOCKET.
8. In the web app, confirm the application appears under Applications for that user.
9. In Settings, revoke the token; saving from the extension should then fail until a new token is created.
```

## Screenshots required by Google (prepare separately)

- At least one screenshot **1280×800** or **640×400** showing the popup with job fields.
- Optional promo tile **440×280**.
- Store icon is already in `icons/icon128.png` (128×128).

Capture these from a real browser session; do not invent UI mockups that differ from the product.

## Package

Upload `docket-chrome-extension.zip` (manifest.json at the ZIP root). Do not include backend, frontend, or secrets.
