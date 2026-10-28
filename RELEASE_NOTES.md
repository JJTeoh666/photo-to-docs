# PhotoToDocs 1.0.1

Adds **Settings → Check for updates** to PhotoToDocs. The repository is now public, so release checks and APK downloads need no GitHub account or token.

## New in this version

- A Settings button opens update controls and Local AI setup.
- Check for updates compares the installed version with the latest published GitHub release.
- Newer versions show Download update, opening the matching release in your browser.
- Clear results handle up-to-date builds, offline connections, incomplete releases, and GitHub request limits.

## Install

Download **PhotoToDocs-debug.apk** below and install it over your existing app. It uses the same signing key as v1.0.0, preserving saved notes and the downloaded model. Allow installation from your file manager if Android prompts you. This is a development-signed APK for personal use, targeting **Android 8+ on ARM64 phones**; 8 GB or more RAM is recommended.

On a first install, open **Settings → Local AI setup** to download or import the approximately **2.59 GB Gemma 4 E2B model**. Allow around 3 GB of free storage plus space for your photos. Model weights are downloaded separately and are not included in this release. After setup, inference works on the phone without an account or API key.

## Included

- Camera/gallery import, page ordering, crop, rotation, and original-photo comparison.
- Bundled printed-text OCR, immediately available without downloading the AI model.
- Local AI photo transcription and selected-text/document editing with reviewable suggestions.
- Manual Markdown/LaTeX editing, preview, undo, revision protection, and local autosave.
- Genuine Word `.docx` files with editable text, tables, and native equations, plus standalone `.tex` exports.
- Exact editable TeX retained when an equation cannot be converted safely to Word.

## Downloads

- **PhotoToDocs-debug.apk** — installable Android app.
- **PhotoToDocs-source.zip** — source code and bundled third-party source/notices, excluding signing keys and build caches.
- **SHA256SUMS.txt** — SHA-256 checksums for the APK and source archive.
- **README.md** — setup, usage, and build instructions.
- **VERIFICATION.md** — completed checks and phone-specific checks still required.
- **THIRD_PARTY_NOTICES.md** — dependency notices.

## Verification and limitations

**31 Kotlin/JVM tests, 14 equation-conversion tests, and 11 Android emulator tests passed.** These include update/version/error handling, Settings controls, real photo processing and bundled OCR, editing, disk persistence, previews, and exports. A live emulator check read the public GitHub release without credentials. The unchanged export pipeline was previously checked by editing and reopening a generated file in Microsoft Word with all seven sample equations preserved, and compiling a generated LaTeX file.

Gemma inference, full model transfer/resumption, memory use, and GPU/CPU fallback still require testing on your physical phone. No physical phone was connected during development. Handwriting, difficult equations, and complex page layouts require manual review. This version edits notes created in the app; importing existing Word files or complete LaTeX projects is outside this release.
