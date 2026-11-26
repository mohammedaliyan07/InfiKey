package com.infikey.layout

/** Bengali (Bangla/Assamese letters via popups), Ol Chiki (Santali). */
object EastIndicLayouts {

    private fun navRow(spaceLabel: String) = row(
        Key("?123", action = KeyAction.SYMBOLS, width = 1.3f),
        Key("\uD83C\uDF10", label = "\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
        Key(",", width = 1f),
        Key("space", label = spaceLabel, output = " ", action = KeyAction.SPACE, width = 4.4f),
        Key(".", width = 1f, popups = listOf("?", "!")),
        Key("⏎", action = KeyAction.ENTER, width = 1.5f)
    )

    val BENGALI = Layout(
        id = Layouts.BENGALI, name = "বাংলা", script = "Bengali", hasCase = false,
        main = listOf(
            row(
                key("ক", listOf("১")), key("খ", listOf("২")), key("গ", listOf("৩")), key("ঘ", listOf("৪")),
                key("ঙ", listOf("৫")), key("চ", listOf("৬")), key("ছ", listOf("৭")), key("জ", listOf("৮")),
                key("ঝ", listOf("৯", "ঞ")), key("ট", listOf("০"))
            ),
            row(
                key("ঠ"), key("ড", listOf("ঢ়")), key("ঢ", listOf("ঢ়")), key("ণ"), key("ত", listOf("ৎ")),
                key("থ"), key("দ"), key("ধ"), key("ন")
            ),
            row(
                key("প"), key("ফ"), key("ব", listOf("ৱ")), key("ভ"), key("ম"),
                key("য", listOf("য়")), key("র"), key("ল"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("শ", listOf("ষ", "স")), key("স", listOf("শ")), key("হ", listOf("ং", "ঃ", "ঁ")),
                key("অ", listOf("আ", "া", "্", "ঽ", "ৠ")),
                key("ই", listOf("ঈ", "ী", "ি")), key("উ", listOf("ঊ", "ূ", "ু")),
                key("এ", listOf("ঐ", "ে", "ৈ", "ও", "ো", "ৌ", "ঔ", "ড়", "অ্যা"))
            ),
            navRow("বাংলা")
        )
    )

    val OL_CHIKI = Layout(
        id = Layouts.OL_CHIKI, name = "ᱥᱟᱱᱛᱟᱲᱤ (Ol Chiki)", script = "Ol Chiki", hasCase = false,
        main = listOf(
            row(
                key("ᱚ"), key("ᱛ"), key("ᱜ"), key("ᱝ"), key("ᱞ"),
                key("ᱟ"), key("ᱠ"), key("ᱡ"), key("ᱢ"), key("ᱣ")
            ),
            row(
                key("ᱤ"), key("ᱥ"), key("ᱦ"), key("ᱧ"), key("ᱨ"),
                key("ᱩ"), key("ᱪ"), key("ᱫ"), key("ᱬ")
            ),
            row(
                key("ᱭ"), key("ᱮ"), key("ᱯ"), key("ᱰ"), key("ᱱ"),
                key("ᱲ"), key("ᱴ"), key("ᱵ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ᱶ"), key("ᱷ"), key("ᱸ"), key("ᱹ"), key("ᱺ"),
                key("ᱽ"), key("।"), key("ᱼ")
            ),
            navRow("ᱥᱟᱱᱛᱟᱲᱤ")
        )
    )
}
