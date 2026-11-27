# BUILDING.md — building InfiKey locally and on CI

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 (Temurin works) |
| Android SDK | compileSdk 35 |
| Gradle | 8.10.2 (wrapper config included) |
| Android Gradle Plugin | via `com.android.application` plugin (see `build.gradle`) |
| NDK | any recent r27+ (CI uses 27.0.12077973) |
| CMake | 3.22.1 |
| Git | to fetch llama.cpp |

Kotlin + Jetpack Compose builds run entirely from the Android project in this repo —
`app/build.gradle` pins every option.

## One-time: fetch llama.cpp

The native build links **llama.cpp**, which is *not* committed to this repo. Fetch it once:

```bash
bash app/src/main/cpp/fetch_llama_cpp.sh
```

- clones llama.cpp into `app/src/main/cpp/llama.cpp/` (skipped if present),
- defaults to `master` because LFM2.5 support needs a recent llama.cpp,
- pin a tag with `LLAMA_CPP_TAG=<tag>`.

## Build

```bash
# debug APK
gradle assembleDebug          # or: ./gradlew if you use the wrapper
# → app/build/outputs/apk/debug/app-debug.apk

# release (unsigned)
gradle assembleRelease
```

Native build options (from `app/build.gradle`) that matter:

- `ANDROID_STL=c++_shared`
- llama.cpp extras off: `LLAMA_BUILD_COMMON/TESTS/EXAMPLES/SERVER=OFF`, `LLAMA_CURL=OFF`
- `GGML_OPENMP=OFF`, `GGML_NATIVE=OFF` (reproducible cross-device builds)
- **ABIs: `arm64-v8a` + `x86_64`** — 32-bit ARM is intentionally not built; llama.cpp's
  llamafile SGEMM uses F16 NEON intrinsics that don't exist on armv7.

Install on a device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```
app/src/main/
├── cpp/                  # JNI bridge (infikey_jni.cpp), CMakeLists, fetch script
├── java/com/infikey/
│   ├── ai/               # engines: on-device, cloud, router, prompts, model manager
│   ├── feature/          # Field Genius (field-type detection + chips)
│   ├── font/             # bundled + imported + Google-Fonts catalog
│   ├── ime/              # IME service, input logic, suggestions, clipboard, glide
│   ├── layout/           # 12 scripts incl. transliteration engine
│   ├── prefs/            # 87-feature registry, settings storage, per-app memory
│   ├── settings/         # Compose UI: MainActivity, SetupActivity
│   ├── theme/            # themes, photo backgrounds, share codes
│   └── view/             # keyboard view, suggestion bar, AI/emoji/clipboard panels
├── assets/dicts/         # 11 word-frequency dictionaries (see SOURCES.md)
```

## CI (GitHub Actions)

`.github/workflows/build.yml` defines one job:

| Job | Trigger | Output |
| --- | --- | --- |
| **Debug APK** | every push/PR to `main` | artifact `InfiKey-debug-apk` |

(The former ~750 MB "APK with pre-installed model" job was removed — models are
downloaded or imported on the device, never packed into the APK.)

The CI runner does exactly what a local build does: checkout → JDK 17 → Gradle 8.10.2 →
`fetch_llama_cpp.sh` → `gradle assembleDebug`.

Artifacts appear on the run page under **Artifacts**; install by enabling *install unknown
apps* for your browser/file manager.

## Regenerating the dictionaries

`assets/dicts/<lang>.txt` files are plain frequency-ordered word lists (one word per line,
UTF-8, no spaces). They are generated from public corpora — sources, licenses and the
rebuild script are documented in
[`app/src/main/assets/dicts/SOURCES.md`](../app/src/main/assets/dicts/SOURCES.md).
