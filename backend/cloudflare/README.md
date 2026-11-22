# TestHelper: user authorization for the existing Worker

Status (2026-11-22): deployed to existing testhelper-ai with Firebase and SQLite AI_QUOTA. Secrets preserved. Real Firebase signup/signin and 401/403 checks passed; Gemini returned a real valid two-question quiz using retained legacy admin access. The full verified-user Firebase path passed on 2026-11-22: existing-account signin, fresh verified Firebase JWT, production Worker and a real Gemini two-question quiz (HTTP200, 3005ms), without legacy admin access. See ../../docs/CONTINUE.md for current version, rollback and exact remaining check.

The Android app discovers public Firebase configuration from GET /api/v1/auth/config at its fixed HTTPS production base URL. Firebase Email/Password authentication owns passwords; Android stores sessions encrypted with Android Keystore in noBackupFilesDir. No shared server token or Gemini key is bundled in the APK. The Firebase Web API key is public client configuration, not an Admin credential.

Before deploying:

1. Owner approves creating a free Firebase Spark project (no billing). Enable Email/Password, a password policy of at least 8 characters, email verification and email enumeration protection. Review Firebase terms and support/contact identity with the owner.
2. Configure FIREBASE_PROJECT_ID and FIREBASE_WEB_API_KEY in existing Worker testhelper-ai. Keep GEMINI_API_KEY and TESTHELPER_ACCESS_TOKEN secrets unchanged. Never paste service-account JSON into client configuration.
3. Owner confirms the SQLite Durable Object binding AI_QUOTA and migration auth-quota-v1 in wrangler.toml. Check the account's current free limits; do not enable paid services automatically. Deploy these modules to the EXISTING Worker, not a new service.
4. Run node --test backend/cloudflare/tests/security.test.mjs. It uses generated RSA test keys and mocked Firebase/quota storage, not live accounts.
5. Test a real verified email account: signup, verification, refresh, quizzes, flashcards, review, extraction, logout, deletion. Confirm live quota behavior and deletion URL. Do not call those scenarios tested until completed.

Authorization checks: RS256 signature from Google's fixed JWKS endpoint, exact project audience and issuer, expiration, issued/authentication times, nonempty subject, password provider and verified email. A Firebase lookup on each request rejects deleted/disabled/revoked sessions. Positive account results are not cached. An invalid/missing configuration fails closed.

Rate limits are enforced atomically in one globally named Durable Object: 3 AI requests/minute and 20/day per user, 10/minute and 60/day per IP, 200/day globally. Authentication verification is capped at 30/minute/IP. Each generation reserves quota before Gemini, including upstream failures. Same user/request ID cannot reserve twice that day. Counts survive Worker restarts. Quota records contain hashes/counters only, expire by day with minute alarms, and do not contain documents, emails or tokens. These are conservative beta caps, not a monetary billing guarantee; set provider spending controls in the owner's account too.

Existing API paths and response shapes are preserved. Legacy TESTHELPER_ACCESS_TOKEN remains for administrative compatibility and is subject to generation quotas. It must never be distributed inside a public APK.

PDF/JPEG/PNG extraction: authenticated POST /api/v1/ai/extract, body {mimeType,data}, strict base64, signature check and decoded size <=3 MiB. Ordinary quiz/review bodies <=64 KiB. Text <=12000 characters, questions/cards 1–25. Gemini generation has a 27-second timeout and bounded output. Output is validated and AI answers remain inferred until user review. Prompt instructions reduce injection risk but cannot guarantee model behavior.

GET /delete-account serves a same-origin HTML form with strict CSP. It signs into Firebase and calls authenticated DELETE /api/v1/account with a recent ID token. The Worker deletes the Firebase account before erasing UID-specific quota/replay records; passwords/tokens are not stored by the page or Worker. This page becomes available only after deployment. Local documents and attempts are not deleted by account removal. Provider retention/logging rules remain applicable; see the privacy draft.

Production rollout and its exact remaining verified-user check are recorded in ../../docs/CONTINUE.md.

DELETE /api/v1/account accepts an authenticated unverified account too, so email confirmation is not a prerequisite for deletion. Recent authentication <=5 minutes is required. IP/global aggregate counters retain their short TTL and are not reset by deleting an account. Provider deletion and quota cleanup are two systems: a failure between them may require support/TTL cleanup. Cloudflare SQLite point-in-time recovery may retain deleted records in provider-managed history for up to 30 days; logical TTL is not a promise of physical erasure.
