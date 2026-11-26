# MODELS.md — on-device AI models in InfiKey

InfiKey runs GGUF models locally through llama.cpp (compiled as a native library inside the
APK — see [`app/src/main/cpp/`](../app/src/main/cpp/)). Nothing you type has to leave the
phone; the model runs in the keyboard process itself.

## The default model

| | |
| --- | --- |
| Model | [LFM2.5-1.2B-Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF) by LiquidAI |
| Quantization | Q4_K_M |
| Size | ~731 MB |
| Download URL | `https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf` |
| Mirror | `https://huggingface.co/unsloth/LFM2.5-1.2B-Instruct-GGUF/resolve/main/…` (automatic fallback) |

One tap on **AI → Load default model** starts the download (with a Wi-Fi check, live progress
and the mirror fallback). The file is written to a `.part` temp file and renamed only after a
size sanity check, so a failed download never leaves a broken "model" behind.

LLM2.5 support needs a *recent* llama.cpp — the build script clones `master` by default (pin
a tag with `LLAMA_CPP_TAG=<tag> bash app/src/main/cpp/fetch_llama_cpp.sh`).

## Where models live

Models are stored in the app-private external files dir:

```
Android/data/com.infikey/files/models/
```

You can drop `.gguf` files into that folder straight from a PC (USB/MTP) — **ModelManager
scans the folder on every open**, validates each file and lists them, biggest first. A model
whose name contains "LFM2.5" is treated as the default candidate.

## Validation rules

A file is accepted as a model only if:

1. it exists and is at least **5 MB** (tiny files can't be real models),
2. the first 4 bytes are the **`GGUF` magic**,
3. for downloads: the result is at least **10 MB**, otherwise it is treated as truncated.

Invalid imports are deleted automatically, so the models folder never fills with junk.

## Importing any GGUF

Three ways, all equivalent:

- **Storage import** — the Android file picker (SAF); the file is stream-copied into the
  models folder with a progress callback, then validated.
- **Folder drop** — copy into `Android/data/com.infikey/files/models/` (see above).
- **URL import** — give the app a direct link to a `.gguf`; it downloads through the same
  validated pipeline.

Any instruct GGUF works. Practical guidance:

| Model class | RAM needed (approx) | Experience |
| --- | --- | --- |
| 0.5–1.2B Q4_K_M | ~0.8–1 GB | Smooth on most devices |
| 3B Q4_K_M | ~2–2.5 GB | Good on flagship/mid phones |
| 7B+ Q4_K_M | ~4–5 GB | High-end devices only, slower |

Loading uses a **2048-token context** by default with CPU inference (`gpuLayers = 0`); see
`AiRouter.loadOnDevice()`.

## The bundled APK

Running the **Build InfiKey APK** workflow manually (Actions → Run workflow) produces a
second artifact: the workflow downloads the default model in CI and packs it into the APK
(~750 MB). On first run the app extracts it from `assets/models/` into the models folder
exactly once (tracked by a marker flag), so the phone needs no download at all.

## GGUF storage details

- `gradle.properties`/`build.gradle` sets `androidResources { noCompress 'gguf' }` — GGUF
  assets are stored uncompressed for instant mmap-style extraction.
- Deleting a model deletes the real file from disk (no orphan GBs).
- The model list is rebuilt from the folder on every open — nothing to refresh.

## Chat templates

The bundled build compiles llama.cpp including its tokenizer/template handling, so Jinja
chat templates embedded in GGUF metadata are honored — instruction-tuned models answer
correctly without manual prompt hacking. InfiKey's own prompts (`AiPrompts.kt`) are layered
on top as system + user messages.
