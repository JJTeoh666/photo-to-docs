# Third-party notices

The app includes these open-source components. Versions are pinned in the Gradle and npm lock files.

| Component | License | Use |
| --- | --- | --- |
| AndroidX, Jetpack Compose | Apache-2.0 | Native UI, lifecycle, file sharing and local WebView assets |
| Kotlin and kotlinx.coroutines | Apache-2.0 | Application code and background work |
| Google LiteRT-LM | Apache-2.0 | On-device text and image model inference |
| KaTeX 0.19.1 | MIT | Offline equation preview and MathML generation |
| mathml2omml 0.5.0 | LGPL-3.0-or-later | MathML to editable Office Math conversion |
| @xmldom/xmldom 0.9.12 | MIT | Validate and normalize equation XML before Word export |
| Manrope | SIL Open Font License 1.1 | Bundled offline interface typeface; license in app assets |

KaTeX and mathml2omml notices are packaged in `app/src/main/assets/licenses/`. The corresponding mathml2omml source, published build metadata, and license are supplied in `web/vendor-sources/mathml2omml/`, including the upstream source files used to produce the published package. The application's use of this library is in `web/src/preview.js`; rebuilding is documented in `web/build.mjs`, `web/package.json`, and the npm lock file.

To replace or modify mathml2omml, install a compatible local replacement in `web/`, run `npm run build`, and rebuild the APK. There is no production signing restriction in this personal debug build; the source can be rebuilt and installed with your own development key. Retain the notices and corresponding source when redistributing this component.

Google ML Kit Text Recognition is distributed under [Google's ML Kit terms](https://developers.google.com/ml-kit/terms). Its bundled recognition model is used for on-device printed text OCR.

Gemma model weights are downloaded separately and are not included in the APK. The selected [Gemma 4 E2B deployment artifact](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) is marked Apache-2.0. The setup screen identifies this model and its source.

Other transitive libraries retain their respective licenses; dependency metadata is available through `./gradlew :app:dependencies`.
