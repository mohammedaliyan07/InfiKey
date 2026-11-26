package com.infikey.ime

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

/**
 * Pure-text helpers behind the smart chips: inline calculator (#41),
 * Indian lakh/crore grouping (#72), Indic numerals (#51), date chips (#54),
 * emoji-from-text (#79), auto-emoji (#80), case cycling (#50), clipboard
 * cleaning (#75), script detection (#45). No state, no permissions.
 */
object SmartText {

    // ---- inline calculator: "=12*(3+4)" → "84" ----
    fun calc(input: String): String? {
        val expr = input.trim().removePrefix("=").trim()
        if (expr.isEmpty() || !expr.matches(Regex("[0-9+\\-*/%(). ]+"))) return null
        if (!expr.any { it in "+-*/%" }) return null
        return runCatching { eval(expr).strip() }.getOrNull()
    }

    private fun eval(s: String): Double {
        val tokens = Regex("\\d+\\.?\\d*|[+\\-*/%()]").findAll(s).map { it.value }.toList()
        val out = ArrayDeque<Double>()
        val ops = ArrayDeque<Char>()
        fun precedence(c: Char) = when (c) { '+', '-' -> 1; '*', '/', '%' -> 2; else -> 0 }
        fun apply() {
            val b = out.removeLast(); val a = out.removeLast()
            out.addLast(when (ops.removeLast()) {
                '+' -> a + b; '-' -> a - b; '*' -> a * b
                '/' -> if (b == 0.0) throw ArithmeticException("div0") else a / b
                else -> if (b == 0.0) throw ArithmeticException("mod0") else a % b
            })
        }
        var prev = ""
        for (t in tokens) {
            val num = t.toDoubleOrNull()
            when {
                num != null -> out.addLast(num)
                t == "(" -> ops.addLast('(')
                t == ")" -> { while (ops.isNotEmpty() && ops.last() != '(') apply(); ops.removeLastOrNull() }
                else -> {
                    val unary = t == "-" && (prev.isEmpty() || prev in listOf("+", "-", "*", "/", "%", "("))
                    if (unary) out.addLast(0.0)
                    while (ops.isNotEmpty() && ops.last() != '(' && precedence(ops.last()) >= precedence(t[0])) apply()
                    ops.addLast(t[0])
                }
            }
            prev = t
        }
        while (ops.isNotEmpty()) { if (ops.last() == '(') ops.removeLast() else apply() }
        return out.lastOrNull() ?: throw ArithmeticException("empty")
    }

    private fun Double.strip(): String =
        if (this == floor(this) && !isInfinite() && kotlin.math.abs(this) < 1e15) toLong().toString()
        else "%.4f".format(this).trimEnd('0').trimEnd('.')

    /** 1234567 → "12,34,567" — Indian lakh/crore digit grouping. */
    fun lakh(digits: String): String {
        val neg = digits.startsWith("-")
        val d = digits.trimStart('-')
        if (d.length <= 3) return digits
        val last3 = d.takeLast(3)
        var rest = d.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) { groups.add(rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) groups.add(rest)
        return (if (neg) "-" else "") + groups.reversed().joinToString(",") + "," + last3
    }

    private val INDIC_ZERO = mapOf(
        "devanagari" to 0x0966, "bengali" to 0x09E6, "tamil" to 0x0BE6,
        "telugu" to 0x0C66, "kannada" to 0x0CE6, "malayalam" to 0x0D66,
        "gujarati" to 0x0AE6, "gurmukhi" to 0x0A66, "odia" to 0x0B66,
        "urdu" to 0x06F0
    )

    /** 123 → १२३ for the active script layout; null when not applicable. */
    fun indicDigits(s: String, layoutId: String): String? {
        if (!s.any { it.isDigit() }) return null
        val zero = INDIC_ZERO[layoutId] ?: return null
        return s.map { if (it in '0'..'9') (zero + (it - '0')).toChar() else it }.joinToString("")
    }

    fun dateChips(): List<Pair<String, String>> {
        val cal = Calendar.getInstance()
        val day = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val today = day.format(cal.time)
        cal.add(Calendar.DATE, 1)
        val tomorrow = day.format(cal.time)
        val weekday = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Calendar.getInstance().time)
        return listOf("📅 $today" to today, "🌙 $tomorrow" to tomorrow, "📆 $weekday" to weekday)
    }

    private val WORD_EMOJI = mapOf(
        "happy" to "😊", "birthday" to "🎂", "love" to "❤️", "thanks" to "🙏", "thank" to "🙏",
        "congrats" to "🎉", "congratulations" to "🎉", "sorry" to "🙏", "good" to "👍", "great" to "👍",
        "nice" to "👌", "fire" to "🔥", "sad" to "😢", "cry" to "😭", "laugh" to "😂", "funny" to "😂",
        "food" to "🍛", "chai" to "☕", "coffee" to "☕", "tea" to "🍵", "sleep" to "😴", "tired" to "🥱",
        "work" to "💼", "meeting" to "📅", "exam" to "📚", "study" to "📚", "win" to "🏆", "diwali" to "🪔",
        "festival" to "🎊", "rain" to "🌧️", "morning" to "🌅", "night" to "🌙", "wow" to "😮",
        "angry" to "😠", "heart" to "❤️", "hug" to "🫂", "party" to "🎉", "gift" to "🎁",
        "phone" to "📱", "money" to "💰", "ok" to "👌", "done" to "✅"
    )

    fun emojiFor(text: String): String? {
        val words = text.lowercase().trim().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        return words.lastOrNull { WORD_EMOJI.containsKey(it) }?.let { WORD_EMOJI[it] }
    }

    /** Auto-emoji: fitting emoji for a finished sentence. */
    fun autoEmoji(sentence: String): String? {
        if (sentence.length < 4) return null
        return emojiFor(sentence)?.let { " $it" }
    }

    /** Case cycle: lower → UPPER → Title → lower … */
    fun caseCycle(word: String): String {
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty()) return word
        return when {
            letters.all { it.isUpperCase() } -> word.lowercase().replaceFirstChar { it.uppercaseChar() }
            letters.all { it.isLowerCase() } -> word.uppercase()
            else -> word.lowercase()
        }
    }

    /** Strips zero-width and control characters (clipboard → clean text). */
    fun cleanText(s: String): String = s.filter {
        val c = it.code
        !(c == 0x200B || c == 0x200C || c == 0x200D || c == 0xFEFF ||
            (c < 0x20 && c != '\n'.code && c != '\t'.code))
    }

    /** Script of a text sample, as a layout id — used for auto-switch hints. */
    fun scriptOf(s: String): String? = when {
        s.any { it.code in 0x0900..0x097F } -> "devanagari"
        s.any { it.code in 0x0980..0x09FF } -> "bengali"
        s.any { it.code in 0x0A00..0x0A7F } -> "gurmukhi"
        s.any { it.code in 0x0A80..0x0AFF } -> "gujarati"
        s.any { it.code in 0x0B00..0x0B7F } -> "odia"
        s.any { it.code in 0x0B80..0x0BFF } -> "tamil"
        s.any { it.code in 0x0C00..0x0C7F } -> "telugu"
        s.any { it.code in 0x0C80..0x0CFF } -> "kannada"
        s.any { it.code in 0x0D00..0x0D7F } -> "malayalam"
        s.any { it.code in 0x0600..0x06FF } -> "urdu"
        s.any { it.code in 0x1C50..0x1C7F } -> "ol_chiki"
        else -> null
    }
}
