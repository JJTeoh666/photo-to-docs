# Verification — PhotoToDocs 1.3.0

Checked on 29 October 2026. Release version 1.3.0 packages the verified development APK as `PhotoToDocs-debug.apk` with the accompanying source archive and checksums.

## 1.3.0 PDF export and notification removal

The final debug APK, instrumentation APK, all 68 JVM cases, and lint pass. Lint reports 0 errors, 19 warnings, and 1 hint. The working ChatGPT response-format fix is preserved; no real account requests or token inspection were used for this change.

The APK manifest reports version 1.3.0 / code 6. Its signing certificate matches the established development signer, so it can update the existing installation. The task-owned emulator was stopped after verification; no project Gradle/compiler Java processes remained.

The new Android device test exercises Export → Save PDF, explicitly chooses Android's Save as PDF destination, cancels the first attempt, retries, and saves through DocumentsUI. The note source stays unchanged. In Graphite Dark, the resulting five-page PDF has white paper, nonblank pages, and its original title and source content. The test cleans up only its own note and restores the previous theme.

The app-generated `verification/sample.pdf` was pulled from the emulator, parsed with pypdf/pdfplumber, and rendered with Poppler. All five rendered pages were visually inspected. The document includes fractions, radicals, integrals/sums, a matrix, aligned equations, a long equation scaled to the available width, a table, accented/Chinese text, and visible TeX for an invalid equation. All 28 prose sections and the final marker survived extraction; text remains within the printable margins. PDF extraction checks normalize Unicode ligatures and compatibility glyphs.

The save destination resets initial print margins on this installed image. The exporter applies at least 18 mm margins on every layout and uses a consistent 72 DPI PDF coordinate system; the vector text/math remain scalable. This avoids the clipped layout observed with this WebView's printer-resolution margin handling. Rendering waits for the offline scripts and fonts; cancellation and completion release its temporary WebView. No network requests are needed for the export.

Removed the application's SnackbarHost and routine notices for photo import, accepted AI changes, saved exports, and cancelled tasks. Important recovery errors retain the existing error dialog; progress remains in the existing busy banner. The photo pipeline test no longer calls the removed notification method.

All 12 additional Android regression cases pass: offline equation/Word/LaTeX conversion, native typing, undo, preview, autosave and reopening, search/tabs, settings, basic photo import/crop/rotate/OCR/acceptance, and all three appearance cases. Together with the new PDF case, 13 device tests pass on the existing Studio AVD at its unchanged 2048 MB RAM setting.

Logs: `pdf-final-build.log`, `verification/pdf-device-test.log`, and `verification/pdf-regression-tests.log`. The sections below record the earlier 1.2.x work unless explicitly marked as rerun.

## 1.2.1 response-format check

The phone screenshot establishes that the 1.2.0 app rejected a successful HTTP response at its strict stream content-type check. It does not show the actual status code, content type, response body, or request ID. The user reports the same failure on Wi-Fi and mobile data. No developer access to their account tokens or server response was obtained.

The hotfix checks media types case-insensitively, detects valid event streams with missing/incorrect content types, and accepts only completed Responses objects/events from JSON replies. It retains the document completion marker requirement. Error responses keep their known error code and show safe HTTP/type/request-ID metadata; raw body content is not shown. There are no automatic retries or model/billing fallbacks.

The hotfix build, all 68 JVM tests, and lint pass. Ten new tests reproduce mixed-case, missing and mislabelled stream headers, completed JSON, incomplete JSON rejection, HTTP-200 JSON errors, HTML replies, detail-only admission errors, unrelated JSON, and stream failure diagnostics. Privacy assertions verify that arbitrary response-body content is not echoed in errors. The focused Android provider test passes on the Studio emulator: it sends the actual prepared JPEG, reads streamed photo output and completed JSON editing output, retains the completion marker requirement, and leaves the original photo unchanged. Responses/credentials are synthetic. Logs: `response-fix-build.log`, `response-fix-test-build.log`, and `verification/response-fix-device-test.log`.

The remaining sections record the earlier 1.2.0 feature verification unless explicitly marked as a hotfix rerun.

## Completed

| Check | Result |
| --- | --- |
| Android debug APK and instrumentation APK | Build passed |
| Kotlin/JVM unit tests | 68 passed for 1.2.1 |
| Offline equation-conversion Node tests | 14 passed |
| Android emulator instrumentation | 23 cases passed across regression and focused OAuth runs |
| Public GitHub update check | Live check on the emulator read the public release without credentials |
| APK update signing | Final v1.2.0 package checked against the established development signer |
| Android lint | 0 errors; remaining warnings concern pinned dependency versions, the ARM64-only target, and minor style suggestions |
| Actual Microsoft Word compatibility | Opened app-generated DOCX, edited text/equation formatting, saved, reopened; all 7 equation objects and their text survived |
| Actual LaTeX compilation | Representative app-generated `.tex` compiled successfully with pdfLaTeX |
| Visual review | Library/editor/preview screenshots and Word/LaTeX rendered pages inspected |

The emulator was the installed Medium Phone API 36.1 image (x86_64 with ARM64 translation), run in read-only mode. The shipped ARM64 APK was installed and launched successfully.

The existing emulator regression tests cover:

- Actual bundled WebView equation conversion and invalid-equation fallback.
- Representative native exports with fractions, radicals, powers, integrals/sums, matrices, aligned equations, and editable tables.
- Source editing, preview, disk autosave, reopening, and activity recreation.
- Rapid native key input preserving every character in the editor and saved file; undo and missing-model guidance while preserving manual editing.
- Library search by title and source, empty search results, selected tab semantics, and editing preserved across Preview/Photos/Edit. Preview verification waits for actual visible text, KaTeX output, and loaded fonts.
- A generated printed photo imported through the app, cropped, rotated, recognized by bundled ML Kit, reviewed as a proposal, accepted, and persisted to disk. Its uncropped original stayed unchanged.
- Appearance switching to Ocean Light and Graphite Dark, exact rendered canvas colors, saved preference on disk, activity recreation, System behavior, and actual WebView preview recoloring without changing the source.
- Settings navigation, installed-version display, Check for updates, busy-state duplicate prevention, available-update download actions, and retry/browser actions after failure.

Unit tests also cover stale AI proposals, atomic JSON persistence, delayed saves, damaged note recovery, XML/LaTeX escaping, malformed formulas, model HTTP range validation, incomplete AI output, and editing input limits.

Update tests cover numeric version comparison (including 1.10 versus 1.9), equal/older releases, draft/incomplete releases, unexpected links, offline errors, bad JSON, and GitHub rate limits. The Word/LaTeX compatibility checks below were performed on the unchanged export pipeline when v1.0.0 was built.

A real Word round trip caught an upstream converter bug that discarded an equality sign. The app now normalizes Word equation runs, validates symbol preservation, creates growing matrix delimiters, and retains unsupported math as visible editable TeX. Regression tests and the final Word round trip pass.

## UI verification

The broad viewport, font-size, and keyboard checks below were performed for the v1.1 workspace refresh. The new v1.2 connection card was separately reviewed at the emulator's normal phone viewport in Ocean Light and Graphite Dark.

Reviewed the library, editor, rendered preview, and Settings in light and dark themes. Ocean and Graphite foreground/background checks passed the 4.5:1 AA threshold, including text on the gradient endpoints. The selected Figma palettes have minimum checked ratios of 5.02:1 (Ocean) and 5.94:1 (Graphite). Controls use 48dp targets and native selected/disabled semantics; formatting icons have accessible names and Settings details announce expanded/collapsed state.

Checked a 375dp small-phone viewport, the emulator's normal 411dp viewport, and 800dp tablet portrait/landscape layouts. Tested system font scaling at 2.0 and all three Android animation scales at zero. Flow rows wrap actions and text; documents remain reachable by scrolling. In short landscape layouts, views/formatting use a menu. With the keyboard leaving less than 160dp of space, the text editor uses the available area; hiding the keyboard restores the controls. This typing case was checked visually after a toolbar-height issue was found and fixed.

The saved Appearance choice controls the app and Android system bar icon contrast. Preview follows the app theme colors and the system font scale, with a 65-character reading measure. The export pipeline itself is unchanged. Physical TalkBack exploration and phone keyboard variants have not been exhaustively tested.

## ChatGPT verification

New JVM tests exercise the RFC PKCE vector, first/returning registration URLs, callback state/client validation, declined consent, catalog parsing, eligibility/usage distinctions, JWT signatures/issuer/audience/nonce/expiry, rotating refresh serialization, cancellation during token commit, account separation, expired-session recovery, supported inference request fields, and offline disconnect. Stream tests cover multiline/CRLF events, visible text versus reasoning, canonical completion, late failure, interrupted streams, and the separate document completion marker.

Android checks exercise a real Chrome callback to the app's `127.0.0.1` listener, rejection of a forged state before a valid callback, Keystore-encrypted persistence/reopening, explicit provider persistence, and missing cloud connection preserving the note. Synthetic transport tests exercise the real photo/text provider and inline JPEG payload without contacting OpenAI or loading local weights.

The broad run passed the sixteen existing document/UI regression cases and six OAuth cases. The remaining browser lifecycle case caught a callback-socket cancellation error, which was corrected. All seven OAuth cases then passed together on the final APK, including returning from Chrome, activity recreation with the same ViewModel, and cancellation. Together with 58 JVM and 14 Node tests, this covers 95 passing automated cases. Raw logs are `verification/oauth-final-tests.log`, `verification/oauth-focused-tests.log`, `oauth-build.log`, and `verification/oauth-node-tests.log`; earlier failed diagnostic runs are retained for context.

A manual tap on Continue with ChatGPT reached OpenAI's actual browser sign-in page. No credentials were entered and no account authorization was completed. Public OIDC discovery was read successfully and matched the documented issuer, endpoints, JWKS location, and RS256 algorithm. **This does not verify this user's eligibility.** The app's Test connection must complete a real inference after their consent.

The app's global cleartext prohibition is unchanged. Only the native callback listener accepts HTTP, bound to loopback. Authorization transactions remain in memory; process termination discards them and requires fresh sign-in. Token records stay under encrypted non-backup storage, and no desktop Codex credentials were accessed.

`dist/examples/` contains files generated by the actual Android export pipeline. `dist/screenshots/` contains development emulator screenshots. Raw build/test logs and visual fixtures are in the ignored `verification/` directory and root `.log` files.

## Requires your phone

The Gemma model weights were **not downloaded or executed during development**. The app uses the real LiteRT-LM SDK, but model accuracy, generation speed, peak RAM use, and GPU/CPU fallback still require testing on your particular ARM64 phone. No physical phone was connected.

On first use, install the APK, download/import the pinned 2.59 GB model in Settings → Local AI, and try a clear photo with one simple equation. Then try a short selected-text AI edit. Review both suggestions before accepting them. After installation, turn off the network to confirm the complete local workflow on your device.

Camera capture through your installed camera app, external file-picker destinations, and Android's share sheet should also receive a quick device check. Emulator tests exercise actual photo processing and native exports but do not control third-party camera/storage apps.

The live multi-gigabyte transfer, interrupted transfer recovery on a phone, low-storage behavior, and memory-pressure behavior have defensive code and unit-tested protocol checks; they were not stress-tested with the full model. Handwriting and complex page layouts need manual review.
