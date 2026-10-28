# PhotoToDocs

A personal Android app for turning photographed study notes into documents you can edit yourself or with on-device AI, then export as Word (`.docx`) or LaTeX (`.tex`).

Version 1.1.0 adds the redesigned document workspace, Ocean light and Graphite dark themes, and a saved Appearance setting.

Download the APK and source archive from [GitHub Releases](https://github.com/JJTeoh666/photo-to-docs/releases/latest). The repository and release downloads are public.

## Install and use

1. Download `PhotoToDocs-debug.apk` from [Releases](https://github.com/JJTeoh666/photo-to-docs/releases/latest), or copy the local `dist/PhotoToDocs-debug.apk` to your Android phone, then open it. Allow installation from that file manager when Android asks. This is a development-signed APK for personal use.
2. Open **Settings**, scroll to **Local AI**, and download Gemma 4 E2B, or import the exact model file linked below. The download is about **2.59 GB**. Use Wi-Fi and keep the app open while downloading; interrupted downloads can be resumed. Allow approximately 3 GB of free storage, plus space for your photos.
3. Choose **Take photo** or **Gallery**. On **Photos**, arrange pages and crop or rotate each photo if needed.
4. Choose **AI: text + math** to transcribe text and equations. **Basic text** works immediately using bundled ML Kit and is intended for printed text; it does not reconstruct mathematical layout.
5. Review the suggestion and accept it. Compare equations with the uncropped photo. Recognition can make mistakes, especially with handwriting, blur, small symbols, or complex layouts.
6. Use **AI edit** to request changes. Use **Edit** for Markdown and LaTeX source and **Preview** to see formatted text and equations. Notes save automatically to the phone. Select a passage before requesting an AI edit, or edit the whole note. Each request currently accepts up to 4,000 source characters and an 800-character instruction.
7. Save or share **Word** and **LaTeX** from the export menu. You can reopen your saved notes in the app and continue editing them.

After the model is installed, photo transcription, AI edits, previews, and exports work offline. There is no API key or login. This build targets 64-bit ARM phones with Android 8 or newer; 8 GB or more RAM is recommended. Actual speed and memory use depend on your phone. A GPU failure triggers a CPU retry. If the model cannot load, basic OCR, manual editing, and exports remain available.

## Interface

The document workspace uses bundled Manrope typography and labeled outline controls. **Settings → Appearance** offers **Light** (Ocean, blue to teal), **Dark** (Graphite, slate and teal), and **System** (follow the phone). The choice applies immediately and stays saved after restarting the app. Capture panels and AI preview actions use the selected gradient; notes and document previews use readable solid surfaces. Search saved notes by title or content. AI edit and Export stay beside the editor navigation; Edit, Preview, and Photos use clear selected tabs. A compact toolbar preserves editing room with the keyboard open and in landscape. On short displays, Document views and formatting opens the tabs and editing actions from one menu. When a landscape keyboard leaves very little room, the editor focuses on your text; hiding the keyboard restores the controls.

## Editing and exports

- The saved document is Markdown with inline `$x^2$` / `\(x^2\)` and display `$$...$$` / `\[...\]` equations. Formatting buttons insert the source syntax.
- Headings, paragraphs, bold/italic, lists, and simple Markdown tables are supported. Preview and both exports use the same parser.
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

Photos, note content, and inference stay on the phone; this app does not send them to an AI service. Storage is app-private and Android cloud backup is disabled. Uninstalling the app removes its notes and model, so save exports you need to keep.

Codex is not integrated in this phone-only version. OpenAI documents ChatGPT authentication for local integrations through [Codex App Server](https://learn.chatgpt.com/docs/app-server); a PC companion would be a separate feature and Luna access would depend on the account.

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
- `documents/`: shared source parser, preview, Word and LaTeX exporters.
- `ui/`: Compose library, editor, photo controls, and setup.

See `VERIFICATION.md` for the checks performed on this build and the remaining phone-specific checks.
