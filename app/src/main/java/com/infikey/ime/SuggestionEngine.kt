package com.infikey.ime

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Dictionary-backed suggestions: completions, next-word prediction and
 * autocorrect. Dictionaries are plain word lists in assets/dicts/<lang>.txt,
 * most-frequent first; learned user words are kept in memory + prefs.
 */
class SuggestionEngine(private val context: Context) {

    companion object {
        const val TAG = "SuggestionEngine"

        /** layout id → dictionary language code */
        val LANG_OF_LAYOUT = mapOf(
            "latin_qwerty" to "en",
            "devanagari" to "hi",
            "bengali" to "bn",
            "tamil" to "ta",
            "telugu" to "te",
            "kannada" to "kn",
            "malayalam" to "ml",
            "gujarati" to "gu",
            "gurmukhi" to "pa",
            "odia" to "or",
            "urdu" to "ur"
        )
        private const val MAX_DICT = 60_000
    }

    data class Entry(val word: String, val freq: Int)

    @Volatile
    private var lang: String = "en"

    private val words = LinkedHashMap<String, Int>(1024)
    private val bigrams = HashMap<String, MutableList<Pair<String, Int>>>() // "a b" → [(next, count)]
    private val userWords = HashMap<String, Int>()

    fun currentLang() = lang

    fun loadForLayout(layoutId: String) {
        val target = LANG_OF_LAYOUT[layoutId] ?: "en"
        if (target == lang && words.isNotEmpty()) return
        lang = target
        words.clear()
        runCatching {
            context.assets.open("dicts/$target.txt").use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).useLines { lines ->
                    var rank = 0
                    for (line in lines) {
                        val w = line.trim().lowercase()
                        if (w.isEmpty() || w.contains(' ')) continue
                        if (words.size >= MAX_DICT) break
                        // earlier in file = more frequent; keep first (max) rank
                        words.putIfAbsent(w, MAX_DICT - rank)
                        rank++
                    }
                }
            }
            loadLearned()
        }.onFailure { Log.w(TAG, "dict $target not loadable", it) }
    }

    // ---- user learning ----

    private fun loadLearned() {
        val sp = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
        sp.getStringSet("learned_$lang", emptySet())?.forEach { userWords[it.lowercase()] = MAX_DICT + 100 }
    }

    fun learn(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty() || w.contains(' ')) return
        userWords[w] = (userWords[w] ?: MAX_DICT) + 1
        val sp = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
        val set = sp.getStringSet("learned_$lang", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(w)
        sp.edit().putStringSet("learned_$lang", set).apply()
    }

    fun forget(word: String) {
        val w = word.trim().lowercase()
        userWords.remove(w)
        val sp = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)
        val set = sp.getStringSet("learned_$lang", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(w)
        sp.edit().putStringSet("learned_$lang", set).apply()
    }

    /** Drops everything learned that is still held in memory. */
    fun clearLearned() {
        userWords.clear()
        bigrams.clear()
    }

    fun knows(word: String): Boolean {
        val w = word.trim().lowercase()
        return userWords.containsKey(w) || words.containsKey(w)
    }

    // ---- lookups ----

    private fun freq(word: String): Int {
        val w = word.lowercase()
        return userWords[w] ?: words[w] ?: 0
    }

    /** Completions for a partially typed word + top next-word predictions. */
    fun suggest(currentWord: String, prevWord: String?, max: Int = 3): List<String> {
        val typed = currentWord.trim()
        val out = LinkedHashSet<String>()
        if (typed.isEmpty()) {
            if (prevWord != null) {
                bigrams[prevWord.lowercase()]?.sortedByDescending { it.second }
                    ?.take(max)?.forEach { out.add(it.first) }
            }
            if (out.isEmpty()) out.addAll(topWords(max))
            return out.toList()
        }
        val lower = typed.lowercase()
        // exact prefix completions first
        var scanned = 0
        synchronized(words) {
            for ((w, f) in words) {
                if (scanned++ > 20_000) break
                if (w.startsWith(lower) && w != lower) out.add(w)
                if (out.size >= max * 4) break
            }
        }
        userWords.keys.filter { it.startsWith(lower) && it != lower }.forEach { out.add(it) }
        if (typed.isNotEmpty()) out.add(typed) // keep raw typing as first-class option
        // rank: learned words, then dictionary rank, then alphabetical
        return out.sortedWith(
            compareByDescending<String> { userWords.containsKey(it.lowercase()) }
                .thenByDescending { freq(it) }
                .thenBy { it.length }
        ).take(max).map {
            if (typed.first().isUpperCase()) it.replaceFirstChar { c -> c.uppercaseChar() } else it
        }
    }

    private fun topWords(n: Int): List<String> {
        synchronized(words) {
            return words.entries.take(300).sortedByDescending { it.value }.take(n).map { it.key }
        }
    }

    /** Minimal edit-distance-1 correction against the dictionary. */
    fun correct(word: String): String? {
        if (word.isEmpty() || knows(word)) return null
        val w = word.lowercase()
        val len = w.length
        var best: Pair<String, Int>? = null
        synchronized(words) {
            for ((cand, f) in words) {
                if (kotlin.math.abs(cand.length - len) > 1) continue
                if (dist1(w, cand)) {
                    if (best == null || f > best!!.second) best = cand to f
                }
            }
        }
        return best?.first
    }

    private fun dist1(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            var diff = 0
            for (i in a.indices) if (a[i] != b[i] && ++diff > 1) return false
            return diff == 1
        }
        val (shorter, longer) = if (a.length < b.length) a to b else b to a
        if (longer.length - shorter.length != 1) return false
        var i = 0
        var j = 0
        var skipped = false
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) { i++; j++ }
            else if (!skipped) { skipped = true; j++ }
            else return false
        }
        return true
    }

    /** Learn a bigram from committed text for next-word prediction. */
    fun learnBigram(prev: String, next: String) {
        val key = prev.trim().lowercase()
        val nxt = next.trim().lowercase()
        if (key.isEmpty() || nxt.isEmpty()) return
        bigrams.getOrPut(key) { mutableListOf() }.let { list ->
            val existing = list.firstOrNull { it.first == nxt }
            if (existing != null) list[list.indexOf(existing)] = nxt to existing.second + 1
            else list.add(nxt to 1)
            if (list.size > 20) list.removeAt(0)
        }
    }

    /** Adds dictionary letters for glide scoring. */
    fun scoreGlide(letters: List<String>, max: Int = 3): List<String> {
        if (letters.size < 2) return emptyList()
        val first = letters.first().lowercase()
        val counts = letters.groupingBy { it.lowercase().first() }.eachCount()
        val result = mutableListOf<Pair<String, Int>>()
        synchronized(words) {
            for ((w, f) in words) {
                if (!w.startsWith(first) || w.length < letters.size) continue
                val wc = w.groupingBy { it }.eachCount()
                var hit = 0
                for ((ch, c) in counts) if ((wc[ch] ?: 0) >= c) hit += c
                if (hit * 100 >= letters.size * 70) {
                    result.add(w to f + hit * 10)
                    if (result.size > 400) break
                }
            }
        }
        return result.sortedByDescending { it.second }.take(max).map { it.first }
    }
}
