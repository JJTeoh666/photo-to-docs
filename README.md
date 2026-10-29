# PhotoToDocs

A personal Android app for turning photographed study notes into documents you can edit yourself, with local Gemma, or with your ChatGPT plan, then export as PDF (`.pdf`), Word (`.docx`), or LaTeX (`.tex`).

Version **1.3.0** adds PDF export and removes bottom snackbar messages. It includes Sign in with ChatGPT and the working response-format fix. Download the APK from Releases and install it over the existing app to preserve notes and your saved sign-in.

Download the APK and source archive from [GitHub Releases](https://github.com/JJTeoh666/photo-to-docs/releases/latest). The repository and release downloads are public.

## Install and use

1. Download `PhotoToDocs-debug.apk` from [Releases](https://github.com/JJTeoh666/photo-to-docs/releases/latest), or copy the local `dist/PhotoToDocs-debug.apk` to your Android phone, then open it. Allow installation from that file manager when Android asks. This is a development-signed APK for personal use.
2. Choose an AI provider in **Settings → AI provider**. For ChatGPT, follow the sign-in instructions below; no Gemma download is needed. For local AI, scroll to **Local AI** and download/import Gemma 4 E2B (about **2.59 GB**). Use Wi-Fi and keep the app open while downloading; interrupted downloads can resume. Allow approximately 3 GB of free storage for Gemma, plus space for photos.
3. Choose **Take photo** or **Gallery**. On **Photos**, arrange pages and crop or rotate each photo if needed.
4. Choose **AI: text + math** to transcribe text and equations. **Basic text** works immediately using bundled ML Kit and is intended for printed text; it does not reconstruct mathematical layout.
5. Review the suggestion and accept it. Compare equations with the uncropped photo. Recognition can make mistakes, especially with handwriting, blur, small symbols, or complex layouts.
6. Use **AI edit** to request changes. Use **Edit** for Markdown and LaTeX source and **Preview** to see formatted text and equations. Notes save automatically to the phone. Select a passage before requesting an AI edit, or edit the whole note. Each request currently accepts up to 4,000 source characters and an 800-character instruction.
7. Open **Export → Save PDF (.pdf)**, select **Save as PDF** in Android's print screen, and tap the PDF save button to choose a folder. Word and LaTeX can also be saved or shared from Export. Reopen your saved notes in the app to continue editing them.

With Gemma installed and Local AI selected, photo transcription and AI edits work offline without a login. ChatGPT requires internet and your browser sign-in. Basic OCR, manual editing, previews, and exports work without either AI connection. This build targets ARM64 phones with Android 8 or newer; 8 GB or more RAM is recommended for Gemma. Its GPU failure triggers a CPU retry. Selecting ChatGPT releases an idle local engine without deleting its model.

## Connect your ChatGPT plan

1. Open **Settings → AI provider → Continue with ChatGPT**. Sign in and approve the app's ChatGPT plan permission in your system browser. Return to PhotoToDocs after the browser confirms receipt. The attempt expires after five minutes and can be cancelled.
2. The app loads your account's models. Luna is selected when `gpt-6-luna` is offered; otherwise choose one from the returned list. **Load models** refreshes the list.
3. Tap **Test connection**. It sends a small text request using your plan allowance. A completed response displays the model and verification time. Successful sign-in or a model list alone does not establish eligibility.
4. Select **ChatGPT** as the AI provider when you want to use it. Signing in leaves the existing provider unchanged. Photo transcription and AI edit now use the selected ChatGPT model, with the same accept/reject review as local AI.

OpenAI documents this flow for eligible Plus/Pro users and personal projects running locally. Account/workspace restrictions, serving-region policy, model availability, and usage limits can affect access. The app distinguishes ineligibility, a usage limit, and temporary failures. **Manage usage** opens ChatGPT's usage controls. No fallback to another provider or billing path occurs automatically.

You can add/switch account registrations, reconnect, or disconnect in Settings. Tokens are encrypted with an Android Keystore key under non-backed-up private storage. Disconnect attempts remote revocation and clears local tokens; if offline, it tells you revocation was not confirmed. Disconnect the app in ChatGPT Settings too in that case. A cancelled or process-terminated sign-in starts fresh; pending authorization codes and PKCE secrets are not persisted.

The local build has automated browser-callback, cryptography, and synthetic API tests. Developer testing does not use your real account credentials or perform a real model request. Your reported phone screenshot confirms sign-in and model listing worked; successful inference still requires Test connection on your phone. See [OpenAI's OAuth guide](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [models/inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference), and [account/session management](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions).

If v1.2.0 showed **ChatGPT did not return a response stream**, install v1.2.1 and tap Test connection again. The old app rejected any successful reply whose content-type header did not start with the exact lowercase stream type, without reading that reply. The fix accepts valid event streams even with unusual headers, and completed Responses JSON objects. Incomplete results still cannot become proposals. Other failures show HTTP status, reply format, and an available request ID without exposing the response body or credentials. The old message alone does not establish an account restriction or identify what the server returned.

## Interface

The document workspace uses bundled Manrope typography and labeled outline controls. **Settings → Appearance** offers **Light** (Ocean, blue to teal), **Dark** (Graphite, slate and teal), and **System** (follow the phone). The choice applies immediately and stays saved after restarting the app. Capture panels and AI preview actions use the selected gradient; notes and document previews use readable solid surfaces. Search saved notes by title or content. AI edit and Export stay beside the editor navigation; Edit, Preview, and Photos use clear selected tabs. A compact toolbar preserves editing room with the keyboard open and in landscape. On short displays, Document views and formatting opens the tabs and editing actions from one menu. When a landscape keyboard leaves very little room, the editor focuses on your text; hiding the keyboard restores the controls.

## Editing and exports

- The saved document is Markdown with inline `$x^2$` / `\(x^2\)` and display `$$...$$` / `\[...\]` equations. Formatting buttons insert the source syntax.
- Headings, paragraphs, bold/italic, lists, and simple Markdown tables are supported. Preview and all exports use the same parser.
- PDF export renders the current note title, formatted text, equations, and tables offline using the bundled preview renderer and Android's print service. It starts with A4 paper and 18 mm margins; the system screen offers paper, orientation, and page selection. Pages stay white in dark mode. Invalid equations remain visible as marked TeX. PDF is a formatted copy; continue editing the saved note or use Word/LaTeX for an editable export.
- Word exports are actual OOXML documents with editable text, tables, and native Office Math (OMML) equations. An unsupported or invalid equation is preserved as clearly marked editable TeX text with “Equation needs review.” It is never silently dropped.
- LaTeX exports are standalone `article` documents using `amsmath` and `amssymb`. Compile them in a LaTeX editor. Preview renders document content and equations; it is not a complete TeX page compiler. Arbitrary custom packages/macros are outside the supported subset.
- AI results are proposals. Accept/reject and undo are available. A proposal cannot overwrite text edited while generation was running; rerun it on the current revision instead.
- This version edits photo-created notes. Importing existing `.docx` / full `.tex` projects and importing external Word changes are not implemented.

## Check for updates

Open **Settings → Check for updates** to compare your installed version with the latest published GitHub release. A newer version shows **Download update**, which opens its release page in your browser. Download the APK and confirm the update in Android. The public release can be checked without a GitHub account or token. Checks run only when you tap the button, and show a retry message if you are offline or GitHub is unavailable.

Install updates over the existing app rather than uninstalling it to keep notes and the downloaded model. Published APKs from this workspace use the same development signing key. A build made elsewhere with a different signing key cannot update the existing installation. Update checking follows [GitHub's latest-release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## Local model

Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.

Artifact: [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), file **`gemma-4-E2B-it.litertlm`**. Web-only and device-specific NPU variants are different files and will not pass the import check.

- Pinned revision: `1caa527c1e88b55f046fcd8d2b4265606680663d`
- Exact size: `2,588,147,712` bytes
- SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
- [Pinned download](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm)

The app checks size and SHA-256 before installation. Downloads resume into a `.part` file with validated HTTP range responses. Model weights are not included in the source or APK. You can remove the model from Settings independently of your notes.

With Local AI, photos, note content, and inference stay on the phone. When ChatGPT is selected, submitted photo pixels, OCR hints, and document text are sent to OpenAI for the requested AI action. Notes and original photos remain saved locally. Storage is app-private and Android cloud backup is disabled. Uninstalling removes notes, model, and saved ChatGPT connections, so save exports you need to keep.

The ChatGPT integration calls the documented Responses API directly from this phone. It does not run Codex App Server or require a laptop companion. The loopback listener accepts only the matching browser callback on `127.0.0.1`; the app's global HTTP prohibition remains enabled.

## Build

Requirements: JDK 17+ (Android Studio's JBR works), Android SDK platform 36/build-tools, Node.js for rebuilding the bundled preview assets, and internet for initial dependencies. Compatible versions are pinned in the build files; the Gradle wrapper verifies its distribution checksum.

On Windows:

```powershell
.\build.ps1
```

The helper discovers the installed SDK and Android Studio JDK, and keeps its debug key and build caches in the project. Output: `app/build/outputs/apk/debug/app-debug.apk`.

You can override discovery with `-SdkPath 'C:\path\to\Android\Sdk' -JdkPath 'C:\path\to\jdk'`. After a successful build, run `./package.ps1` to refresh the APK, source archive, and checksums in `dist/`.

On other systems set `JAVA_HOME`, `ANDROID_HOME`, and `local.properties`, then run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

To rebuild offline preview assets:

```sh
cd web
npm ci
npm test
npm run build
```

Assets are already included, so Node is not required for an ordinary Android build. The corresponding third-party source and notices are in `app/src/main/assets/licenses/`; see `THIRD_PARTY_NOTICES.md`.

With a connected test phone/emulator:

```powershell
.\build.ps1 -Tasks @('connectedDebugAndroidTest')
```

An x86_64 emulator UI/export test build is supported with `-PpreviewAbi=x86_64`; local AI deliberately requires an ARM64 phone. Emulator checks do not establish phone inference speed or photo accuracy.

## Source map

- `ai/`: model installation, HTTP resumption, integrity checking, native inference, and cancellation.
- `data/`: atomic note storage, revision-safe suggestions, and photo preparation.
- `documents/`: shared source parser, preview, PDF, Word, and LaTeX exporters.
- `ui/`: Compose library, editor, photo controls, and setup.

See `VERIFICATION.md` for the checks performed on this build and the remaining phone-specific checks.
