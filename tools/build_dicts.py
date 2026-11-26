#!/usr/bin/env python3
"""Build InfiKey dictionaries: assets/dicts/<lang>.txt

Format contract (see SuggestionEngine.kt): one word per line, most-frequent
first, no spaces in a line, UTF-8.

Sources, in fallback order:
  1. wasi-master/wmkeyboard-data  (data/<lang>/<lang>_full.txt.gz)
  2. hermitdave/FrequencyWords    (content/2018/<lang>/<lang>_50k.txt, OpenSubtitles)
  3. wordfreq 3.1.1 pip package   (installed in the system python)

Every word is filtered: real script letters only, no ASCII/digit noise,
deduped, capped at 15k lines.
"""
import gzip
import io
import os
import re
import sys
import urllib.request
import unicodedata

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
         "app", "src", "main", "assets", "dicts")
CAP = 15_000

# lang -> (unicode block ranges allowed, extra allowed ascii)
LANGS = {
    "en": ("latin", re.compile(r"^[a-z']+$")),
    "hi": ((0x0900, 0x097F), None),
    "bn": ((0x0980, 0x09FF), None),
    "gu": ((0x0A80, 0x0AFF), None),
    "pa": ((0x0A00, 0x0A7F), None),
    "or": ((0x0B00, 0x0B7F), None),
    "ta": ((0x0B80, 0x0BFF), None),
    "te": ((0x0C00, 0x0C7F), None),
    "kn": ((0x0C80, 0x0CFF), None),
    "ml": ((0x0D00, 0x0D7F), None),
    "ur": ((0x0600, 0x06FF), None),
}

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "infikey-dict-builder/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def source_wmkeyboard(lang, variant="full"):
    raw = fetch(f"https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/master/data/{lang}/{lang}_{variant}.txt.gz")
    text = gzip.decompress(raw).decode("utf-8", "replace")
    return text, f"wasi-master/wmkeyboard-data (OpenSubtitles/AOSP derived, {variant})"

def source_hermitdave(lang):
    text = fetch(f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{lang}/{lang}_50k.txt").decode("utf-8", "replace")
    return text, "hermitdave/FrequencyWords (OpenSubtitles2018)"

def source_wordfreq(lang):
    from wordfreq import top_n_list
    words = top_n_list(lang, CAP)
    text = "\n".join(words)
    return text, "wordfreq 3.1.1 (MIT)"

# per-language preferred source order (chosen after comparing tops)
ORDER = {
    "en": [(source_wmkeyboard, {"variant": "aosp"}), (source_hermitdave, {}), (source_wordfreq, {})],
    "hi": [(source_wordfreq, {}), (source_wmkeyboard, {})],
    "bn": [(source_hermitdave, {}), (source_wmkeyboard, {})],
    "ml": [(source_hermitdave, {}), (source_wmkeyboard, {})],
    "ta": [(source_wordfreq, {}), (source_wmkeyboard, {})],
    "ur": [(source_wordfreq, {}), (source_wmkeyboard, {})],
    "te": [(source_wmkeyboard, {})],
    "kn": [(source_wmkeyboard, {})],
    "gu": [(source_wmkeyboard, {})],
    "pa": [(source_wmkeyboard, {})],
    "or": [(source_wmkeyboard, {})],
}

EDGES = "।॥.,'\"?!-_—()[]:;…«»“”‘’"

def clean_word(lang, w):
    w = w.strip()
    if lang == "en":
        w = w.lower()
    # strip edge punctuation (danda etc.)
    w = w.strip(EDGES).strip()
    if not w or " " in w:
        return None
    # indic: reject doubled consecutive chars (subtitle junk like ஃஃ)
    if lang != "en":
        for a, b in zip(w, w[1:]):
            if a == b:
                return None
    return w

def word_ok(lang, w):
    spec = LANGS[lang]
    w = w.strip()
    if not w or " " in w:
        return False
    if lang == "en":
        return bool(spec[1].match(w.lower()))
    lo, hi_ = spec[0]
    has_script = any(lo <= ord(c) <= hi_ for c in w)
    if not has_script:
        return False
    # reject anything with ascii letters/digits or weird control chars
    for c in w:
        o = ord(c)
        if o < 0x0600 and not c.isspace():  # ascii/latin junk inside indic word
            if c in "।·.":
                continue
            return False
        cat = unicodedata.category(c)
        if cat.startswith("C"):
            return False
    return True

def build(lang):
    text, src_name = None, None
    for fn, kw in ORDER[lang]:
        try:
            text, src_name = fn(lang, **kw)
            if text.strip():
                break
        except Exception as e:
            print(f"  [{lang}] {fn.__name__} failed: {e}", file=sys.stderr)
    if not text:
        return None
    seen, out = set(), []
    for line in text.splitlines():
        w = clean_word(lang, line.split(" ")[0])
        if w is None or w in seen or not word_ok(lang, w):
            continue
        seen.add(w)
        out.append(w)
        if len(out) >= CAP:
            break
    return out, src_name

def main():
    os.makedirs(OUT, exist_ok=True)
    attrib, report = {}, []
    for lang in LANGS:
        res = build(lang)
        if not res:
            print(f"[{lang}] NO SOURCE SUCCEEDED — needs manual list")
            continue
        words, src = res
        path = os.path.join(OUT, f"{lang}.txt")
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(words) + "\n")
        attrib.setdefault(src, []).append(lang)
        report.append((lang, len(words), " ".join(words[:6])))
    print("\n=== RESULT ===")
    for lang, n, sample in report:
        print(f"{lang}: {n:6d} words | top: {sample}")
    print("\n=== ATTRIBUTION ===")
    for src, ls in attrib.items():
        print(f"{src}: {', '.join(ls)}")

if __name__ == "__main__":
    main()
