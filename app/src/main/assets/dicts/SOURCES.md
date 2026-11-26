# Dictionary sources & licenses

The `assets/dicts/<lang>.txt` files are plain **word-frequency lists**: one word per line,
most frequent first, UTF-8, no spaces. They power completions, autocorrect, next-word
prediction and glide scoring (`SuggestionEngine.kt`). The keyboard learns your personal
words *on top of* these locally — the bundled files contain only public-corpus words, no
personal data.

| Code | Language | Words | Source | License / attribution |
| --- | --- | --- | --- | --- |
| `en` | English | 15,000 | wasi-master/wmkeyboard-data `en_aosp` (curated AOSP-style list) | data derived from AOSP LatinIME wordlists + OpenSubtitles 2016/2018 (CC-BY-SA, opensubtitles.org) |
| `hi` | Hindi | 13,709 | `wordfreq` 3.1.1 | MIT; data from web-scale corpora |
| `bn` | Bengali | 15,000 | hermitdave/FrequencyWords (OpenSubtitles2018) | CC-BY-SA, opensubtitles.org |
| `ta` | Tamil | 14,158 | `wordfreq` 3.1.1 | MIT |
| `te` | Telugu | 15,000 | wasi-master/wmkeyboard-data `te_full` | OpenSubtitles 2016/2018 (CC-BY-SA) |
| `kn` | Kannada | 15,000 | wasi-master/wmkeyboard-data `kn_full` | OpenSubtitles 2016/2018 (CC-BY-SA) |
| `ml` | Malayalam | 15,000 | hermitdave/FrequencyWords (OpenSubtitles2018) | CC-BY-SA, opensubtitles.org |
| `gu` | Gujarati | 15,000 | wasi-master/wmkeyboard-data `gu_full` | OpenSubtitles 2016/2018 (CC-BY-SA) |
| `pa` | Punjabi | 15,000 | wasi-master/wmkeyboard-data `pa_full` | OpenSubtitles 2016/2018 (CC-BY-SA) |
| `or` | Odia | 15,000 | wasi-master/wmkeyboard-data `or_full` | OpenSubtitles 2016/2018 (CC-BY-SA) |
| `ur` | Urdu | 14,516 | `wordfreq` 3.1.1 | MIT |

Upstream repos:

- https://github.com/wasi-master/wmkeyboard-data — frequency word lists for 215+ languages
- https://github.com/hermitdave/FrequencyWords — OpenSubtitles 2018 frequency lists
- https://github.com/rspeer/wordfreq — MIT-licensed frequency library

Subtitle-derived lists naturally contain film vocabulary. Words were filtered to
language-script characters only, punctuation stripped (e.g. trailing `।`), doubled-letter
junk removed, deduped, capped at 15,000 per language.

## Rebuilding

`tools/build_dicts.py` (repo root) regenerates every file from the sources above with the
same filters. It downloads the raw lists, normalizes them to the format contract above and
writes them into `assets/dicts/`. Requires Python 3.10+ and `pip install wordfreq`.

To swap or extend a dictionary manually, keep the format: one word per line, no spaces,
most-frequent first — the loader keeps the best rank of the first 60,000 lines.
