# FEATURES.md — the full InfiKey feature registry

InfiKey ships **85 features across 6 categories**. Every feature below is real code in this
repository (the registry lives in
[`app/src/main/java/com/infikey/prefs/Features.kt`](../app/src/main/java/com/infikey/prefs/Features.kt)),
and every toggleable one can be switched on/off from **InfiKey Settings → Features**.
Features marked *off* by default are opt-in so the first-run keyboard stays clean.

> **Changelog — this build (37d789b):** every feature below is now wired to real
> behavior, not just a toggle. Newly implemented in this build: number row,
> auto-capitalisation, undo chip + history stack, numbered-list continuation,
> glide trail, spacebar trackpad slide, incognito enforcement on password/phone
> fields + safe-app list, smart chips (inline calculator, lakh/crore grouping,
> Indic numerals, case cycle, emoji-from-text, date chips, Field Genius
> autofill, auto script-switch hint), AI tone check, read-back before send,
> per-app AI tone memory, AI transparency log, Keystore-encrypted cloud keys,
> press-animation styles, sound packs, real background blur, idle glow, theme
> editor, 24-font Google Fonts catalog download, clipboard clean/→script/
> ask-AI/edit, emoji kaomoji + recents tabs, 15 Unicode text styles, biometric
> lock, data audit screen, backup/restore, my-facts editor.

## Typing (24)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 1 | Glide typing | Slide finger across letters to type words | on |
| 2 | Next-word suggestions | 3 predictions above the keyboard | on |
| 3 | Auto-correct | Fix mistyped words automatically | on |
| 4 | Auto-capitalisation | Capitalize sentences and proper nouns | on |
| 5 | Number row | Show digits above letters | off |
| 6 | Long-press popups | Accents, digits and symbols on long-press | on |
| 7 | Haptic feedback | Vibrate on key press | on |
| 8 | Key sounds | Play a sound on key press | off |
| 9 | One-handed mode | Shift keyboard left or right | off |
| 10 | Adjustable key height | Resize keys for any thumb | on |
| 11 | Undo / redo | Shake-free undo of recent edits | on |
| 12 | Cursor drag on spacebar | Move the caret by sliding on space | on |
| 13 | Spacebar trackpad mode | Turn spacebar into a full cursor pad | off |
| 14 | Selection mode | Select text with arrow keys | off |
| 15 | Dedicated number pad | Full keypad for number fields | on |
| 16 | Dev layout | Programming symbols under your thumbs | off |
| 17 | Markdown layout | `**` `##` `` ` `` `[]` shortcuts row | off |
| 18 | Delete word | Backspace removes whole words on swipe | on |
| 19 | Safe grapheme delete | Never splits emoji or Indic clusters | on |
| 20 | Double-space period | Tap space twice for a full stop | on |
| 21 | Voice typing | System speech-to-text | on |
| 22 | Double-tap caps lock | Lock caps with two shift taps | on |
| 23 | Swipe-to-delete | Slide left on backspace to wipe fast | off |
| 24 | Swipe spacebar → switch language | Slide left/right to change script | on |

## Languages & Scripts (16)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 25 | Latin (English + more) | QWERTY with accents | on |
| 26 | Devanagari | Hindi, Marathi, Nepali, Sanskrit | on |
| 27 | Bengali | Bangla, Assamese, Manipuri | on |
| 28 | Tamil | Full vowel/consonant grid | on |
| 29 | Telugu | Full vowel/consonant grid | on |
| 30 | Kannada | Full vowel/consonant grid | on |
| 31 | Malayalam | Full vowel/consonant grid | on |
| 32 | Gujarati | Full vowel/consonant grid | on |
| 33 | Gurmukhi | Punjabi | on |
| 34 | Odia | Odia script grid | on |
| 35 | Urdu (RTL) | Right-to-left Perso-Arabic | on |
| 36 | Ol Chiki | Santali script | on |
| 37 | Live transliteration | Type `namaste` → नमस्ते in 8 languages | off |
| 38 | Per-app language memory | Remembers which language you use in each app | on |
| 39 | Indic number formatting | 1,00,000-style grouping (en-IN) | on |
| 40 | Indic font pack | Noto fonts for every supported script | on |

**Dictionaries:** 11 bundled word-frequency dictionaries power suggestions, completion,
autocorrect and glide scoring — English, Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam,
Gujarati, Punjabi, Odia and Urdu. See
[`assets/dicts/SOURCES.md`](../app/src/main/assets/dicts/SOURCES.md) for sources and licenses.
The keyboard *learns* your words on top of these (kept on-device, see [PRIVACY.md](PRIVACY.md)).

## AI (20)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 41 | AI typing into any field | AI writes directly where paste is blocked | on |
| 42 | AI chat panel | Ask, instruct, iterate without leaving the field | on |
| 43 | Style rewrite bar | 14 tones for the text you're about to send | on |
| 44 | Fix grammar & spelling | One tap polish | on |
| 45 | Continue writing | AI takes over from your draft | on |
| 46 | Smart reply chips | 3 instant replies to any incoming message | on |
| 47 | Chat TL;DR | Summarize long chats in 3 lines | on |
| 48 | Form auto-fill | AI fills fields from your facts | off |
| 49 | Field Genius chips | Context chips per field type (email, URL, OTP…) | on |
| 50 | Festival & greeting generator | Diwali, Eid, Pongal, Christmas… personalised | on |
| 51 | Style DNA | 3 sliders that lock your voice into every output | on |
| 52 | Read aloud before send | Hear the draft before you commit it | off |
| 53 | Multi-model routing | Fast model for fixes, deep model for essays | on |
| 54 | Any-GGUF import | Import any GGUF: storage, folder, or URL | on |
| 55 | Any-site cloud API | Any OpenAI-compatible URL — any provider | on |
| 56 | Per-app AI tone | Formal at work, casual in chats | off |
| 57 | AI transparency log | See exactly what was sent to AI, when | on |
| 58 | Safe-app list | AI never reads fields in chosen apps | on |
| 59 | Fully offline mode | Airplane-proof on-device generation | off |
| 60 | AI translate | Quick translate chip in the panel | off |

## Look & Feel (12)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 61 | Built-in themes | Obsidian Aurora, Paper White, Mint Ink, Sakura Glow, Deep Ocean, Desert Gold, Carbon Mono, Indigo Night, Bamboo Green, Rose Cream | on |
| 62 | Photo keyboard | Any image as keyboard background | on |
| 63 | Blur & dim sliders | Tune photo backdrop live | on |
| 64 | Theme share codes | Export/import themes as a short code | on |
| 65 | Custom theme editor | Your colors, radius and glow | on |
| 66 | Font system | Bundled families + your own | on |
| 67 | Google Fonts catalog | Download OFL fonts in-app | on |
| 68 | Import any .ttf/.otf | Fonts from any website, stored locally | on |
| 69 | Unicode text styles | 𝐁𝐨𝐥𝐝, 𝓢𝓬𝓻𝓲𝓹𝓽, ᴍᴏɴᴏ… works in any app | on |
| 70 | Elder mode | Bigger keys, larger text, high contrast | off |
| 71 | Neumorphic key rendering | Skeuomorphic depth + press glow | on |
| 72 | Idle glow easter egg | Keys breathe light when you pause | on |

## Clipboard & History (6)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 73 | Clipboard history | Last 20 snippets, searchable | on |
| 74 | Pin clipboard items | Keep the essentials on top | on |
| 75 | Clipboard → clean text | Strip weird formatting & zero-width chars | on |
| 76 | Translate clipboard | One-tap translate of copied text | off |
| 77 | Edit before paste | Tweak snippets inline | on |
| 78 | Ask AI about clipboard | Summarize/extract from copied text | on |

## Smart & Fun (7)

| # | Feature | What it does | Default |
| --- | --- | --- | --- |
| 79 | Auto-emoji | Adds a fitting emoji after sentences | off |
| 80 | Text → emoji | Convert words into emoji strings | on |
| 81 | Text macros | `;email` → your address instantly | on |
| 82 | Shape gestures | Circle = undo, V = paste, Z = redo | on |
| 83 | Indian greetings pack | Namaste, Adaab, Vanakkam quick chips | off |
| 84 | Date & time chips | Insert today/tomorrow/timing formats | off |
| 85 | Accessibility | High contrast, large text, TalkBack labels | on |
