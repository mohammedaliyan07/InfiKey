package com.infikey.layout

/**
 * Romanized → Indic script transliteration ("namaste" → नमस्ते).
 * One canonical phonetic key list; every script provides the same-length
 * parallel arrays, so adding a script = adding one table.
 */
object Transliteration {

    // 33 consonant keys, canonical order (retroflexes as tt/tth/dd/ddh/nn).
    private val CONS_KEYS = listOf(
        "k", "kh", "g", "gh", "ng", "ch", "chh", "j", "jh", "ny",
        "tt", "tth", "dd", "ddh", "nn", "t", "th", "d", "dh", "n",
        "p", "ph", "b", "bh", "m", "y", "r", "l", "v", "sh", "ss", "s", "h"
    )

    // 12 vowel keys: a aa i ii u uu e ee ai o oo au
    private val VOWEL_KEYS = listOf(
        "a", "aa", "i", "ii", "u", "uu", "e", "ee", "ai", "o", "oo", "au"
    )

    class Scheme(
        val id: String,
        val cons: List<String>,      // 33 entries, same order as CONS_KEYS
        val vowels: List<String>,    // 12 independent vowels
        val matras: List<String>,    // 12 vowel signs (empty string = none)
        val virama: String,          // halant / killer stroke
        val abjad: Boolean = false   // Urdu: vowel sign = full letter
    )

    val DEVANAGARI = Scheme(
        Layouts.DEVANAGARI,
        cons = "क ख ग घ ङ च छ ज झ ञ ट ठ ड ढ ण त थ द ध न प फ ब भ म य र ल व श ष स ह".split(" "),
        vowels = "अ आ इ ई उ ऊ ए ऐ ऐ ओ औ औ".split(" "),
        matras = listOf("", "ा", "ि", "ी", "ु", "ू", "े", "ै", "ै", "ो", "ौ", "ौ"),
        virama = "्"
    )

    val GUJARATI = Scheme(
        Layouts.GUJARATI,
        cons = "ક ખ ગ ઘ ઙ ચ છ જ ઝ ઞ ટ ઠ ડ ઢ ણ ત થ દ ધ ન પ ફ બ ભ મ ય ર લ વ શ ષ સ હ".split(" "),
        vowels = "અ આ ઇ ઈ ઉ ઊ એ ઐ ઐ ઓ ઔ ઔ".split(" "),
        matras = listOf("", "ા", "િ", "ી", "ુ", "ૂ", "ે", "ૈ", "ૈ", "ો", "ૌ", "ૌ"),
        virama = "્"
    )

    val GURMUKHI = Scheme(
        Layouts.GURMUKHI,
        cons = "ਕ ਖ ਗ ਘ ਙ ਚ ਛ ਜ ਝ ਞ ਟ ਠ ਡ ਢ ਣ ਤ ਥ ਦ ਧ ਨ ਪ ਫ ਬ ਭ ਮ ਯ ਰ ਲ ਵ ਸ਼ ਸ ਸ ਹ".split(" "),
        vowels = "ਅ ਆ ਇ ਈ ਉ ਊ ਏ ਐ ਐ ਓ ਔ ਔ".split(" "),
        matras = listOf("", "ਾ", "ਿ", "ੀ", "ੁ", "ੂ", "ੇ", "ੈ", "ੈ", "ੋ", "ੌ", "ੌ"),
        virama = "੍"
    )

    val BENGALI = Scheme(
        Layouts.BENGALI,
        cons = "ক খ গ ঘ ঙ চ ছ জ ঝ ঞ ট ঠ ড ঢ ণ ত থ দ ধ ন প ফ ব ভ ম য র ল ব শ ষ স হ".split(" "),
        vowels = "অ আ ই ঈ উ ঊ এ ঐ ঐ ও ঔ ঔ".split(" "),
        matras = listOf("", "া", "ি", "ী", "ু", "ূ", "ে", "ৈ", "ৈ", "ো", "ৌ", "ৌ"),
        virama = "্"
    )

    val ODIA = Scheme(
        Layouts.ODIA,
        cons = "କ ଖ ଗ ଘ ଙ ଚ ଛ ଜ ଝ ଞ ଟ ଠ ଡ ଢ ଣ ତ ଥ ଦ ଧ ନ ପ ଫ ବ ଭ ମ ୟ ର ଲ ୱ ଶ ଷ ସ ହ".split(" "),
        vowels = "ଅ ଆ ଇ ଈ ଉ ଊ ଏ ଐ ଐ ଓ ଔ ଔ".split(" "),
        matras = listOf("", "ା", "ି", "ୀ", "ୁ", "ୂ", "େ", "ୈ", "ୈ", "ୋ", "ୌ", "ୌ"),
        virama = "୍"
    )

    val TAMIL = Scheme(
        Layouts.TAMIL,
        // Tamil has no aspirates: they fold onto the plain consonant.
        cons = "க க க க ங ச ச ஜ ஜ ஞ ட ட ட ட ண த த த த ந ப ப ப ப ம ய ர ல வ ஷ ஸ ச ஹ".split(" "),
        vowels = "அ ஆ இ ஈ உ ஊ எ ஏ ஐ ஒ ஓ ஔ".split(" "),
        matras = listOf("", "ா", "ி", "ீ", "ு", "ூ", "ெ", "ே", "ை", "ொ", "ோ", "ௌ"),
        virama = "்"
    )

    val TELUGU = Scheme(
        Layouts.TELUGU,
        cons = "క ఖ గ ఘ ఙ చ ఛ జ ఝ ఞ ట ఠ డ ఢ ణ త థ ద ధ న ప ఫ బ భ మ య ర ల వ శ ష స హ".split(" "),
        vowels = "అ ఆ ఇ ఈ ఉ ఊ ఎ ఏ ఐ ఒ ఓ ఔ".split(" "),
        matras = listOf("", "ా", "ి", "ీ", "ు", "ూ", "ె", "ే", "ై", "ొ", "ో", "ౌ"),
        virama = "్"
    )

    val KANNADA = Scheme(
        Layouts.KANNADA,
        cons = "ಕ ಖ ಗ ಘ ಙ ಚ ಛ ಜ ಝ ಞ ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ವ ಶ ಷ ಸ ಹ".split(" "),
        vowels = "ಅ ಆ ಇ ಈ ಉ ಊ ಎ ಏ ಐ ಒ ಓ ಔ".split(" "),
        matras = listOf("", "ಾ", "ಿ", "ೀ", "ು", "ೂ", "ೆ", "ೇ", "ೈ", "ೊ", "ೋ", "ೌ"),
        virama = "್"
    )

    val MALAYALAM = Scheme(
        Layouts.MALAYALAM,
        cons = "ക ഖ ഗ ഘ ങ ച ഛ ജ ഝ ഞ ട ഠ ഡ ഢ ണ ത ഥ ദ ധ ന പ ഫ ബ ഭ മ യ ര ല വ ശ ഷ സ ഹ".split(" "),
        vowels = "അ ആ ഇ ഈ ഉ ഊ എ ഏ ഐ ഒ ഓ ഔ".split(" "),
        matras = listOf("", "ാ", "ി", "ീ", "ു", "ൂ", "െ", "േ", "ൈ", "ൊ", "ോ", "ൌ"),
        virama = "്"
    )

    val URDU = Scheme(
        Layouts.URDU,
        cons = "ک خ گ غ نگ چ چھ ج جھ ن ٹ ٹھ ڈ ڈھ ن ت تھ د دھ ن پ پھ ب بھ م ی ر ل و ش س س ہ".split(" "),
        vowels = listOf("ا", "آ", "ی", "ی", "و", "و", "ے", "ی", "ائ", "و", "او", "اؤ"),
        matras = listOf("ا", "آ", "ی", "ی", "و", "و", "ے", "ی", "ائ", "و", "او", "اؤ"),
        virama = "",
        abjad = true
    )

    private val SCHEMES: Map<String, Scheme> = listOf(
        DEVANAGARI, GUJARATI, GURMUKHI, BENGALI, ODIA, TAMIL,
        TELUGU, KANNADA, MALAYALAM, URDU
    ).associateBy { it.id }

    fun supports(scriptLayoutId: String) = SCHEMES.containsKey(scriptLayoutId)

    fun schemes() = SCHEMES.values.toList()

    private sealed interface Token {
        data class Cons(val index: Int) : Token
        data class Vowel(val index: Int) : Token
    }

    private data class Raw(val char: Char) : Token

    /** Greedy longest-match the latin input; unmatched characters stay where they are. */
    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len > input.length) continue
                val piece = input.substring(i, i + len).lowercase()
                val ci = CONS_KEYS.indexOf(piece)
                if (ci >= 0) { tokens.add(Token.Cons(ci)); i += len; matched = true; break }
                val vi = VOWEL_KEYS.indexOf(piece)
                if (vi >= 0) { tokens.add(Token.Vowel(vi)); i += len; matched = true; break }
            }
            if (!matched) { tokens.add(Raw(input[i])); i++ }
        }
        return tokens
    }

    /**
     * Transliterate latin text into the script of [layoutId].
     * Consonant clusters get the virama between them ("namaste" → नमस्ते).
     * Unknown characters pass through unchanged.
     */
    fun translate(input: String, layoutId: String): String {
        val scheme = SCHEMES[layoutId] ?: return input
        val tokens = tokenize(input)
        val out = StringBuilder()
        for ((idx, token) in tokens.withIndex()) {
            when (token) {
                is Token.Cons -> {
                    out.append(scheme.cons[token.index])
                    // implicit schwa deletion: write virama only between consonants
                    val nextIsCons = idx + 1 < tokens.size && tokens[idx + 1] is Token.Cons
                    if (nextIsCons && !scheme.abjad) out.append(scheme.virama)
                }
                is Token.Vowel -> {
                    val prevIsCons = idx > 0 && tokens[idx - 1] is Token.Cons
                    out.append(if (prevIsCons) scheme.matras[token.index] else scheme.vowels[token.index])
                }
                is Raw -> out.append(token.char)
            }
        }
        return out.toString()
    }
}
