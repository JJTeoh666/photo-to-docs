# Photo to Docs: a document workspace

Audience: students and people turning photographed notes/equations into editable Word and LaTeX documents. Native Android, Jetpack Compose. Primary job: capture, correct, export.

## Design plan and review

The UI/UX Pro Max search `document scanner productivity mobile --design-system` returned a fitting document-gray/scan-blue palette and functional minimalism. Its marketing-page hero/video pattern does not fit a repeat-use phone workspace. Replace it with direct capture controls and a document list. The existing cream/serif/sage marketing treatment also delays access to saved work; remove that treatment.

Use the scanner's blue frame as the distinctive visual motif, only in the capture area and empty state. Keep document rows quiet and use real source previews. Avoid promotional headlines, all-caps labels, arbitrary stats, decorative gradients and identical cards for every section.

## Selected themes

The user selected Figma A (Ocean) for Light and D (Graphite) for Dark on 28 October 2026. This replaces the earlier cobalt palette proposal. Settings → Appearance offers Light, Dark, and System, saved in local appearance preferences. System follows the phone; explicit Light/Dark override the phone while the app is open and after restart.

- Ocean canvas `#F4F8FC`, sheet `#FFFFFF`, ink `#173149`, secondary text `#52677B`, border `#D8E3ED`, accent `#184CB8`, soft surface `#E8F0FA`.
- Ocean gradient: `#184CB8` to `#087B85`, white foreground.
- Graphite canvas `#18212B`, sheet `#23303E`, ink `#EDF3F8`, secondary text `#BBC8D5`, border `#425468`, accent `#A8D8E6`, soft surface `#2B4150`.
- Graphite gradient: `#234D69` to `#316C76`, white foreground. Light accent buttons use `#143947` foreground.
- Gradients belong to capture panels and AI preview actions. Long notes and equation previews keep solid surfaces.

Type: bundled Manrope, 400/500/600/700. Friendly open letterforms suited to note titles and technical controls; 16/26 body, 14/22 supporting copy, 20/28 section headings, 30/38 screen headings. Source equations remain editable text; preview uses document type, with system font scaling and a readable measure.

Layout: left aligned, 20dp phone gutters, 760dp content maximum. Use 4/8dp rhythm; 8/16/24/32 spacing tiers. Corner hierarchy: 10dp small controls, 16dp rows, 22dp capture/settings panels. No decorative shadows. All actions at least 48dp high; flow rows wrap labels.

```text
Library                 Editor                 Settings
App mark    Settings    Back   Title   Export   Back  Settings
Documents               Edit Preview Photos    App updates + version
Photo actions / frame   [source or preview]    Local AI + state/action
Local AI status         [labeled toolbar]      Details (expandable)
Search (when useful)    AI edit, always visible Privacy + about
Document rows           Photo actions in tab
```

Editor keeps AI separate from tabs. Below 480dp of height (680dp with enlarged text), collapse secondary chrome and expose formatting through a reachable toolbar toggle. Below 280dp of available height, move views and formatting into a top-bar menu to preserve typing room. Below 160dp with the keyboard visible, focus on source text alone until the keyboard is hidden. Never claim a completed save from a timer; say “Saves automatically.”

Native outline vectors share 24-unit geometry and 2-unit strokes. Decorative icons have no descriptions; standalone actions do. Tabs use native selected semantics. Respect system light/dark, safe areas, reduced animation scales and font sizes. No custom automatic animation is needed.

## AI connection controls

Settings keeps the selected Ocean/Graphite tokens. Use native radio semantics for Local AI / ChatGPT; persist an explicit provider choice. Connecting an account does not select cloud processing. Show where submitted content goes beside AI requests. The ChatGPT card uses Continue with ChatGPT, account/model selection, Test connection, Manage usage, and Disconnect. A connected identity alone is not described as verified access. Show a successful model/time only after completed inference. Errors distinguish permissions, eligibility, usage caps, and temporary network failure. Local OCR/editing/exports remain reachable without sign-in.
