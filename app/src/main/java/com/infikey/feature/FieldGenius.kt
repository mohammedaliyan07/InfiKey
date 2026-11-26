package com.infikey.feature

import android.os.Bundle
import android.view.inputmethod.EditorInfo

/**
 * FieldGenius — reads the focused editor and guesses what kind of field it is:
 * search box, email, password, chat, URL, form field... Feeds the AI panel a
 * natural-language field hint and guards privacy (password fields never
 * reach any AI, on-device or cloud).
 */
object FieldGenius {

    enum class Kind(
        val label: String,
        val aiHint: String,
        val privacy: Privacy = Privacy.NORMAL
    ) {
        PASSWORD("Password", "", Privacy.BLOCKED),
        EMAIL("Email", "an email address", Privacy.SUGGEST),
        PHONE("Phone", "a phone number", Privacy.BLOCKED),
        NUMBER("Number", "a numeric field", Privacy.NORMAL),
        URL("URL", "a web address", Privacy.SUGGEST),
        SEARCH("Search", "a search box", Privacy.NORMAL),
        CHAT("Chat", "a chat message", Privacy.NORMAL),
        NAME("Name", "a person's name field", Privacy.SUGGEST),
        MULTILINE("Text", "a multi-line text area", Privacy.NORMAL),
        NORMAL("Text", "a plain text input", Privacy.NORMAL)
    }

    enum class Privacy { NORMAL, SUGGEST, BLOCKED }

    data class FieldInfo(val kind: Kind, val hint: String, val packageName: String)

    fun analyse(editorInfo: EditorInfo?, packageName: String, fieldLabel: String? = null): FieldInfo {
        val hint = fieldLabel
            ?: editorInfo?.hintText?.toString()
            ?: editorInfo?.packageName?.let { null } ?: ""
        val kind = classify(editorInfo, hint)
        return FieldInfo(kind, hint, packageName)
    }

    private fun classify(info: EditorInfo?, hint: String): Kind {
        val t = info?.inputType ?: 0
        val cls = t and EditorInfo.TYPE_MASK_CLASS
        val variation = t and EditorInfo.TYPE_MASK_VARIATION
        return when {
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD -> Kind.PASSWORD
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD -> Kind.PASSWORD
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> Kind.EMAIL
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_URI -> Kind.URL
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME -> Kind.NAME
            cls == EditorInfo.TYPE_CLASS_TEXT && variation == EditorInfo.TYPE_TEXT_VARIATION_PHONETIC -> Kind.CHAT
            cls == EditorInfo.TYPE_CLASS_PHONE -> Kind.PHONE
            cls == EditorInfo.TYPE_CLASS_NUMBER -> Kind.NUMBER
            hint.contains("search", true) -> Kind.SEARCH
            hint.contains("chat", true) || hint.contains("message", true) -> Kind.CHAT
            hint.contains("email", true) -> Kind.EMAIL
            hint.contains("name", true) -> Kind.NAME
            info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 -> Kind.MULTILINE
            else -> Kind.NORMAL
        }
    }

    /** True → never send this field's text to any AI engine. */
    fun isBlocked(info: FieldInfo) = info.kind.privacy == Privacy.BLOCKED

    /** Facts the AI may use when filling forms (kept locally, user-editable). */
    fun formFacts(): List<String> {
        // Lives in prefs so the user can edit; never auto-submitted without AI request.
        return listOf(
            "Full name: (set in InfiKey Settings → AI → Form facts)",
            "Email: (set in InfiKey Settings → AI → Form facts)",
            "Phone: (set in InfiKey Settings → AI → Form facts)"
        )
    }

    /** Chat app detection for reply-chips auto-suggestion. */
    fun isChatApp(pkg: String): Boolean = pkg.contains("whatsapp", true) ||
        pkg.contains("telegram", true) || pkg.contains("messenger", true) ||
        pkg.contains("signal", true) || pkg.contains("instagram", true) ||
        pkg.contains("discord", true) || pkg.contains("gboard", false).not() && pkg.contains("sms", true) ||
        pkg.endsWith(".mms", true)

    /** Bundle from EditorInfo.extras used by some apps to label fields. */
    fun labelFromExtras(extras: Bundle?): String? =
        extras?.getString("android.intent.extra.TITLE") ?: extras?.getString("label")
}
