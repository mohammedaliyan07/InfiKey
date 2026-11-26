package com.infikey.layout

/** Urdu — right-to-left Nastaliq script, rows given in visual (left-to-right) order. */
object UrduLayout {

    private fun navRow() = row(
        Key("⏎", action = KeyAction.ENTER, width = 1.5f),
        Key(".", width = 1f, popups = listOf("؟", "!")),
        Key("space", label = "اردو", output = " ", action = KeyAction.SPACE, width = 4.4f),
        Key(",", width = 1f, popups = listOf("،")),
        Key("\uD83C\uDF10", label = "\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
        Key("?123", action = KeyAction.SYMBOLS, width = 1.3f)
    )

    val LAYOUT = Layout(
        id = Layouts.URDU, name = "اردو", script = "Urdu", rtl = true, hasCase = false,
        main = listOf(
            row(
                key("ا", listOf("آ", "أ", "إ", "ء")), key("ب"), key("پ"),
                key("ت"), key("ٹ"), key("ث"), key("ج"), key("چ"), key("ح"), key("خ")
            ),
            row(
                key("ص"), key("ش"), key("ژ"), key("ز"), key("ڑ"),
                key("ر"), key("ذ"), key("ڈ"), key("د")
            ),
            row(
                key("گ"), key("ک"), key("ق"), key("ف"), key("غ"),
                key("ع"), key("ظ"), key("ط"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ے"), key("ی", listOf("ئ", "ي")), key("ء"), key("ھ"), key("ہ", listOf("ۃ")),
                key("و", listOf("ؤ")), key("ں"), key("ن"), key("م"), key("ل")
            ),
            navRow()
        )
    )
}
