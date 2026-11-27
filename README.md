# InfiKey

**The AI keyboard that writes *for* you — fully offline, or wired to any cloud you choose.**

[![Build InfiKey APK](https://github.com/mohammedaliyan07/InfiKey/actions/workflows/build.yml/badge.svg)](https://github.com/mohammedaliyan07/InfiKey/actions/workflows/build.yml)

InfiKey is an Android keyboard (IME) with a real AI brain built in:

- **On-device AI** — runs [LiquidAI LFM2.5-1.2B-Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF) (Q4_K_M, ~731 MB, downloaded in one tap) via llama.cpp. Works with airplane mode on.
- **Any GGUF** — import *any* GGUF model from your storage, the app folder, or a URL. InfiKey scans, validates and auto-loads it.
- **Any cloud API** — add any OpenAI-compatible endpoint: any provider, any base URL, any model, custom headers. Groq, OpenRouter, Together, Ollama, LM Studio, your own server — all fine.
- **AI types *into* the focused field** — even where paste is blocked. The IME commits text directly through the input connection, so AI-written content lands in ChatGPT boxes, exam apps, banking forms — anywhere.
- **12 Indic scripts + transliteration** — Devanagari, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Gurmukhi, Odia, Urdu (RTL), Ol Chiki, Latin. Live transliteration for 8 languages.
- **Obsidian Aurora design** — near-black surfaces, violet-to-mint gradients, 16 dp rounded neumorphic keys, press glow. Photo backgrounds with blur + dim sliders.
- **Fonts** — bundled families, a Google Fonts (OFL) catalog you download from inside the app, or import any `.ttf`/`.otf`.
- **85 features** — glide typing, clipboard history, undo stack, shape gestures, macros, Field Genius chips, Style DNA, festival generator, per-app memory, elder mode, theme share codes and much more. See [docs/FEATURES.md](docs/FEATURES.md).

## Get the APK

1. Open [Actions](../../actions) → **Build InfiKey APK** → latest run.
2. Download the **InfiKey-debug-apk** artifact and install it (enable *install unknown apps*).

Then download the default model with one tap (AI tab → *Download default model*) or import any GGUF instead.

## Setup (30 seconds)

1. Open InfiKey → **Enable keyboard** → toggle InfiKey on.
2. Tap **Switch keyboard** → pick InfiKey.
3. In the **AI** tab: load the default model (one tap) or import any GGUF, and/or add cloud endpoints.

## Docs

| Doc | What's inside |
| --- | --- |
| [docs/FEATURES.md](docs/FEATURES.md) | All 85 features, categorized |
| [docs/MODELS.md](docs/MODELS.md) | Default model, any-GGUF import, folders, chat templates |
| [docs/CLOUD.md](docs/CLOUD.md) | Connecting any cloud API from any site (examples) |
| [docs/BUILDING.md](docs/BUILDING.md) | Building locally and on CI |
| [docs/PRIVACY.md](docs/PRIVACY.md) | Offline-first design + transparency log |

## Roadmap

- v1.1: floating / split keyboard, dynamic wallpapers
- v1.2: personal dictionary sync, per-key sound packs marketplace

## License

MIT — see [LICENSE](LICENSE).
