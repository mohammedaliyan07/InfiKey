package com.infikey.ai

import org.json.JSONObject

/** InfiKey persona + all task prompts. */
object AiPrompts {

    const val PERSONA =
        "You are InfiKey, the AI inside the InfiKey Android keyboard. " +
        "You help people write quickly in their exact situation. " +
        "Always output ONLY the text the user asked for — no explanations, no quotes, no markdown code fences, no preamble."

    fun compose(what: String, tone: String, fieldHint: String, dna: StyleDna? = null): List<ChatMessage> {
        val dnaLine = dna?.directive() ?: ""
        return listOf(
            ChatMessage.system("$PERSONA $dnaLine"),
            ChatMessage.user(
                "Write exactly what the user asked, ready to paste into an input field.\n" +
                "Field: ${fieldHint.ifBlank { "plain text input" }}\n" +
                "Tone: ${tone.ifBlank { "natural" }}\n" +
                "Request: $what\n" +
                "Rules: no quotation marks around the result, no 'Here is', no explanations."
            )
        )
    }

    fun rewrite(text: String, styles: List<String>): List<ChatMessage> = listOf(
        ChatMessage.system(
            "$PERSONA Rewrite the given text into the requested styles. " +
            "Output each variant on its own line prefixed exactly with its style name and ' — '. " +
            "One line per style, no extra commentary."
        ),
        ChatMessage.user(
            "Styles: ${styles.joinToString(", ")}\n\nText:\n$text"
        )
    )

    fun fix(text: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Fix grammar, spelling and punctuation. Keep meaning and language. Output only the fixed text."),
        ChatMessage.user(text)
    )

    fun continueWriting(draft: String, fieldHint: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Continue the text naturally from where it stops. Output only the continuation, no repetition of the original."),
        ChatMessage.user("Field: ${fieldHint.ifBlank { "text" }}\n\nDraft:\n$draft")
    )

    fun replyChips(context: String, lang: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Produce 3 short reply suggestions for the received message. " +
            "One per line, no numbering, each under 8 words, in $lang."),
        ChatMessage.user(context)
    )

    fun tldr(chat: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Summarize the conversation in at most 3 crisp bullet lines."),
        ChatMessage.user(chat)
    )

    fun festival(festival: String, toName: String, lang: String, tone: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Write a warm, personal festival greeting. Output only the greeting."),
        ChatMessage.user("Festival: $festival\nTo: ${toName.ifBlank { "a friend" }}\nLanguage: $lang\nTone: $tone")
    )

    fun formFill(fieldLabel: String, facts: List<String>): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Write a value for the form field using only the given facts. Output only the value."),
        ChatMessage.user("Field: $fieldLabel\nFacts:\n${facts.joinToString("\n")}")
    )

    fun translate(text: String, targetLang: String): List<ChatMessage> = listOf(
        ChatMessage.system("$PERSONA Translate the text to $targetLang. Output only the translation."),
        ChatMessage.user(text)
    )

    /** Parse "Style — variant" numbered/line output into ordered variants. */
    fun parseVariants(output: String, styles: List<String>): List<String> {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (style in styles) {
            val line = lines.firstOrNull {
                it.startsWith(style, ignoreCase = true) ||
                    it.substringAfter(" — ", "").let { v -> v.isNotEmpty() } == false
            } ?: lines.getOrNull(out.size) ?: ""
            out += line.substringAfter("—", line).trim().removePrefix("—").trim().ifBlank { line }
        }
        return out.filter { it.isNotBlank() }.ifEmpty { listOf(output.trim()) }
    }

    /** #43 — tone check: what does the draft sound like, one soften suggestion. */
    fun toneCheck(text: String): List<ChatMessage> = listOf(
        ChatMessage.system(
            "$PERSONA Analyse the tone of this draft. Reply in exactly two short lines: " +
                "line 1 'Tone: <word>' (rude / blunt / neutral / friendly / formal); " +
                "line 2 one concrete suggestion to soften or improve. Under 30 words total."
        ),
        ChatMessage.user(text.ifBlank { "(empty draft)" })
    )

    fun parseLines(output: String, max: Int): List<String> =
        output.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotEmpty() }.take(max)
}

/** Style DNA — user's tone fingerprint applied to every generation. */
data class StyleDna(
    val formality: Int,   // 0 casual .. 100 formal
    val energy: Int,      // 0 calm .. 100 enthusiastic
    val brevity: Int      // 0 elaborate .. 100 minimal
) {
    fun directive(): String {
        val f = when { formality >= 70 -> "formal and polite"; formality >= 35 -> "neutral"; else -> "casual and friendly" }
        val e = when { energy >= 70 -> "energetic with occasional light exclamation"; energy >= 35 -> "steady"; else -> "calm" }
        val b = when { brevity >= 70 -> "as brief as possible"; brevity >= 35 -> "moderate length"; else -> "detailed" }
        return "Writing style: $f, $e, $b."
    }

    fun toJson(): JSONObject = JSONObject().put("f", formality).put("e", energy).put("b", brevity)

    companion object {
        val DEFAULT = StyleDna(40, 50, 50)
        fun fromJson(o: JSONObject) = StyleDna(o.optInt("f", 40), o.optInt("e", 50), o.optInt("b", 50))
    }
}

/** Rewrite styles offered in the suggestion bar. */
object RewriteStyles {
    data class Style(val id: String, val label: String, val instruction: String)

    val ALL = listOf(
        Style("formal", "Formal", "Rewrite in formal, professional language"),
        Style("casual", "Casual", "Rewrite in relaxed, everyday language"),
        Style("friendly", "Friendly", "Rewrite warmly, like a close friend"),
        Style("short", "Short", "Condense to the shortest clear version"),
        Style("elaborate", "Elaborate", "Expand with helpful detail"),
        Style("polite", "Polite", "Rewrite extra politely and considerately"),
        Style("persuasive", "Persuasive", "Rewrite convincingly and confidently"),
        Style("apologetic", "Apologetic", "Rewrite with sincere apology and humility"),
        Style("funny", "Funny", "Rewrite with light humour"),
        Style("poetic", "Poetic", "Rewrite beautifully, with imagery"),
        Style("genz", "Gen Z", "Rewrite in current Gen-Z internet tone"),
        Style("corporate", "Corporate", "Rewrite in crisp corporate email tone"),
        Style("hinglish", "Hinglish", "Rewrite mixing Hindi and English naturally"),
        Style("urdu_poetic", "Urdu shayari", "Rewrite with Urdu poetic elegance")
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}
