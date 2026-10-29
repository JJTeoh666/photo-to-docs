# PhotoToDocs 1.3.0 — PDF export and ChatGPT

Export formatted notes as PDF and edit without the bottom snackbar notifications. This release also includes Sign in with ChatGPT and the response-format fix confirmed working on the user's phone.

## PDF and quieter editing

- Export → Save PDF (.pdf) opens Android's print screen. Select Save as PDF and choose where to save it.
- Offline PDF pages include the note title, formatted text, equations, lists, and tables. They use white pages in either app theme and default to A4 with 18 mm margins.
- Paper/orientation changes trigger a fresh layout. Long display equations scale to the printable width. Invalid equations stay visible as marked TeX.
- Cancelling a PDF export preserves the note; exporting again starts a new print session.
- Removed bottom messages after photo import, applying AI changes, saving exports, and cancelling tasks. Progress and actionable errors remain visible in their existing locations.

## ChatGPT response-format fix

- Fixes the generic "ChatGPT did not return a response stream" failure for valid responses with different header casing, missing/mislabelled stream headers, or completed JSON Responses objects.
- Still rejects incomplete JSON results, interrupted streams, server errors, and HTML/web pages. It sends no automatic retry or alternate model request.
- Unexpected replies now include safe HTTP status, content type, body-shape category, and an available request ID. Raw response bodies, tokens, and account details are not included in diagnostics.
- Preserves existing account connections when updating. If an earlier build showed a connection error, tap Test connection again after installing this version.

## Sign in with ChatGPT

- Continue with ChatGPT opens OpenAI sign-in in your phone's browser, with PKCE and a loopback-only callback.
- Credentials are encrypted using Android Keystore and excluded from backups and source packages.
- Add/switch account registrations, load account-specific models, reconnect, manage usage, and disconnect.
- Test connection completes a small AI request and records the model/time; sign-in alone is not presented as verified eligibility.
- Settings → AI provider explicitly selects Local AI or ChatGPT. Signing in does not switch providers automatically.
- ChatGPT handles photos and selected-text/whole-note edits without installing Gemma. Submitted content is sent to OpenAI; local AI remains available offline.
- Responses stream as reviewable drafts. Interrupted/incomplete responses and stale edits cannot overwrite a saved note.
- Preserves Ocean Light, Graphite Dark, manual editing, OCR, previews, and native Word/LaTeX exports.

## Install

Download **PhotoToDocs-debug.apk** below and install it over your existing app. It uses the same signing key as v1.0.0/v1.0.1, preserving saved notes and the downloaded model. Allow installation from your file manager if Android prompts you. This is a development-signed APK for personal use, targeting **Android 8+ on ARM64 phones**; 8 GB or more RAM is recommended.

For ChatGPT, open **Settings → Continue with ChatGPT**, complete browser consent, return to the app, choose a model, and tap **Test connection**. Then explicitly select **ChatGPT** as the provider. Eligible account access is determined by OpenAI; no real account login or cloud inference was performed during development. The test uses a small amount of your plan allowance.

For offline AI, open **Settings → Local AI** to download/import the approximately **2.59 GB Gemma 4 E2B model**. Allow around 3 GB of free storage plus space for photos. Model weights are separate from the APK. Local AI needs no account or API key.

## Included

- Camera/gallery import, page ordering, crop, rotation, and original-photo comparison.
- Bundled printed-text OCR, immediately available without downloading the AI model.
- Local AI photo transcription and selected-text/document editing with reviewable suggestions.
- Manual Markdown/LaTeX editing, preview, undo, revision protection, and local autosave.
- Offline PDF exports, genuine Word `.docx` files with editable text, tables, and native equations, plus standalone `.tex` exports.
- Exact editable TeX retained when an equation cannot be converted safely to Word.

## Downloads

- **PhotoToDocs-debug.apk** — installable Android app.
- **PhotoToDocs-source.zip** — source code and bundled third-party source/notices, excluding signing keys and build caches.
- **SHA256SUMS.txt** — SHA-256 checksums for the APK and source archive.
- **README.md** — setup, usage, and build instructions.
- **VERIFICATION.md** — completed checks and phone-specific checks still required.
- **THIRD_PARTY_NOTICES.md** — dependency notices.

## Verification and limitations

**68 JVM tests and 13 Android device tests pass**, with **0 lint errors**. The PDF test saves a five-page document through Android's file picker, checks cancellation and source preservation, and verifies white pages in dark mode. The generated PDF was also rendered and visually reviewed; equations, tables, Chinese text, and all 28 prose sections remained within its margins. Regression checks cover editing, photo import/OCR, themes, and Word/LaTeX exports. See **VERIFICATION.md** for details and earlier ChatGPT checks.

Cloud inference tests use synthetic responses. Eligibility and model quality depend on your phone/account; development did not access your phone's credentials or complete a real model request.

Gemma inference, full model transfer/resumption, memory use, and GPU/CPU fallback still require testing on your physical phone. No physical phone was connected during development. Handwriting, difficult equations, and complex page layouts require manual review. This version edits notes created in the app; importing existing Word files or complete LaTeX projects is outside this release.
