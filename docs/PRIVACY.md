# PRIVACY.md — what InfiKey collects, stores and sends

Short version: **InfiKey has no backend, no analytics and no account.** Your typing lives
on your device. The only network traffic is traffic you explicitly trigger, and every AI
request is logged where you can see it.

## What never happens

- No telemetry, crash-reporting SDKs or analytics of any kind.
- No account, no sign-in, no cloud sync.
- No keystroke logging that leaves the device.
- No advertising.

## What stays on the device

| Data | Where | Why |
| --- | --- | --- |
| Learned words | `SharedPreferences` (`infikey`), per-language set | Suggestions get better; you can *forget* any word |
| Per-app language + tone memory | app-private prefs | Opens WhatsApp in the language you last used there |
| Clipboard history | in-memory + prefs, last 20 items, searchable | Feature 73–78; never uploaded |
| Cloud endpoints (incl. API key) | app-private prefs JSON | So your endpoints survive restarts |
| AI models | `Android/data/com.infikey/files/models/` | On-device inference |
| Downloaded/imported fonts | app-private `fonts/` dir | Look & feel |
| Themes, settings, Style DNA | app-private prefs | Look & feel |

## When the network *is* used

Only these four cases, always user-triggered:

1. **Default model download** — from HuggingFace (with an unsloth mirror fallback), once,
   when you tap *Load default model*. No typing data is involved.
2. **Cloud endpoints you configured** — the text you choose to generate/rewrite/translate
   is sent to the base URL you entered. InfiKey adds nothing else to the request.
3. **Google Fonts catalog** — font downloads from Google Fonts (OFL files), only when you
   tap a font in the catalog.
4. **URL model / URL font imports** — downloads the exact link you provided.

On-device AI (feature 59, *Fully offline mode*) works with airplane mode on: model load,
inference, suggestions, clipboard and learning all run locally.

## Transparency and control features

- **AI transparency log** (feature 57) — see exactly what text was sent to which AI, when.
- **Safe-app list** (feature 58) — InfiKey's AI never reads fields in apps you list there.
- **Per-feature toggles** — all 85 features are switchable in Settings → Features, so any
  capability you don't want can simply be turned off.
- **Forget a word** — long-press a learned suggestion to remove it from the dictionary.

## Permissions

| Permission | Used for |
| --- | --- |
| IME (keyboard) service | The keyboard itself |
| Internet | The four network cases above |
| Storage via SAF picker | Importing GGUF models / fonts — InfiKey reads only what you pick |

No contacts, location, microphone (voice typing uses the system's own speech service),
camera or SMS access.

## Bundled dictionaries

The 11 suggestion dictionaries are static word-frequency lists built from public corpora
(OpenSubtitles 2016/2018 via *wmkeyboard-data* / *hermitdave/FrequencyWords* and the MIT
`wordfreq` data). They contain **no personal data** — sources and licenses in
[`assets/dicts/SOURCES.md`](../app/src/main/assets/dicts/SOURCES.md).

## Verifying the claims

InfiKey is fully open source — every network call lives in
[`ai/ModelManager.kt`](../app/src/main/java/com/infikey/ai/ModelManager.kt) (downloads) and
[`ai/CloudEngine.kt`](../app/src/main/java/com/infikey/ai/CloudEngine.kt) (endpoints). You
can grep the repo for `HttpURLConnection` and confirm nothing else dials out.
