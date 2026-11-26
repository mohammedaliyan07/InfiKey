package com.infikey.layout

/** Tamil, Telugu, Kannada, Malayalam — abugidas of South India. */
object SouthIndicLayouts {

    private fun navRow(spaceLabel: String) = row(
        Key("?123", action = KeyAction.SYMBOLS, width = 1.3f),
        Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
        Key(",", width = 1f),
        Key(label = spaceLabel, output = " ", action = KeyAction.SPACE, width = 4.4f),
        Key(".", width = 1f, popups = listOf("?", "!")),
        Key("⏎", action = KeyAction.ENTER, width = 1.5f)
    )

    val TAMIL = Layout(
        id = Layouts.TAMIL, name = "தமிழ்", script = "Tamil", hasCase = false,
        main = listOf(
            row(
                key("அ", listOf("ா")), key("ஆ", listOf("ா")), key("இ", listOf("ி", "௧")), key("ஈ", listOf("ீ", "௨")),
                key("உ", listOf("ு", "௩")), key("ஊ", listOf("ூ", "௪")), key("எ", listOf("ெ", "௫")),
                key("ஏ", listOf("ே", "௬")), key("ஐ", listOf("ை", "௭")), key("ஃ", listOf("௮"))
            ),
            row(
                key("க", listOf("ங", "௯")), key("ச", listOf("ஞ", "ஜ", "௦")), key("ட", listOf("ண")),
                key("த", listOf("ந")), key("ப", listOf("ம")), key("ய"), key("ர"), key("ல")
            ),
            row(
                key("வ"), key("ழ"), key("ள"), key("ற"), key("ன"),
                key("ஷ"), key("ஸ"), key("ஹ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("்", listOf("ஔ")), key("ா"), key("ி", listOf("ீ")), key("ு", listOf("ூ")),
                key("ெ", listOf("ொ")), key("ே", listOf("ோ")), key("ை"), key("ௌ")
            ),
            navRow("தமிழ்")
        )
    )

    val TELUGU = Layout(
        id = Layouts.TELUGU, name = "తెలుగు", script = "Telugu", hasCase = false,
        main = listOf(
            row(
                key("క", listOf("౧")), key("ఖ", listOf("౨")), key("గ", listOf("౩")), key("ఘ", listOf("౪")),
                key("ఙ", listOf("౫")), key("చ", listOf("౬")), key("ఛ", listOf("౭")), key("జ", listOf("౮")),
                key("ఝ", listOf("౯", "ఞ")), key("ట", listOf("౦"))
            ),
            row(
                key("ఠ"), key("డ"), key("ఢ"), key("ణ"), key("త"),
                key("థ"), key("ద"), key("ధ"), key("న")
            ),
            row(
                key("ప"), key("ఫ"), key("బ"), key("భ"), key("మ"),
                key("య"), key("ర"), key("ఱ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ల"), key("ళ"), key("వ"), key("శ", listOf("స", "ష")), key("స"), key("హ", listOf("ం", "ః")),
                key("అ", listOf("ఆ", "ా", "్", "ఽ")), key("ఇ", listOf("ఈ", "ీ", "ి")),
                key("ఉ", listOf("ఊ", "ూ", "ు")), key("ఎ", listOf("ఏ", "ె", "ే", "ఐ", "ై", "ఒ", "ొ", "ో", "ఔ", "ౌ", "ఋ", "ౠ", "ౄ", "ౕ", "ౖ"))
            ),
            navRow("తెలుగు")
        )
    )

    val KANNADA = Layout(
        id = Layouts.KANNADA, name = "ಕನ್ನಡ", script = "Kannada", hasCase = false,
        main = listOf(
            row(
                key("ಕ", listOf("೧")), key("ಖ", listOf("೨")), key("ಗ", listOf("೩")), key("ಘ", listOf("೪")),
                key("ಙ", listOf("೫")), key("ಚ", listOf("೬")), key("ಛ", listOf("೭")), key("ಜ", listOf("೮")),
                key("ಝ", listOf("೯", "ಞ")), key("ಟ", listOf("೦"))
            ),
            row(
                key("ಠ"), key("ಡ"), key("ಢ"), key("ಣ"), key("ತ"),
                key("ಥ"), key("ದ"), key("ಧ"), key("ನ")
            ),
            row(
                key("ಪ"), key("ಫ"), key("ಬ"), key("ಭ"), key("ಮ"),
                key("ಯ"), key("ರ"), key("ಱ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ಲ"), key("ಳ"), key("ವ"), key("ಶ", listOf("ಸ", "ಷ")), key("ಸ"), key("ಹ", listOf("ಂ", "ಃ")),
                key("ಅ", listOf("ಆ", "ಾ", "್", "ಽ")), key("ಇ", listOf("ಈ", "ೀ", "ಿ")),
                key("ಉ", listOf("ಊ", "ೂ", "ು")), key("ಎ", listOf("ಏ", "ೆ", "ೇ", "ಐ", "ೈ", "ಒ", "ೊ", "ೋ", "ಔ", "ೌ", "ಋ", "ೠ", "ೄ", "ೕ", "ೖ"))
            ),
            navRow("ಕನ್ನಡ")
        )
    )

    val MALAYALAM = Layout(
        id = Layouts.MALAYALAM, name = "മലയാളം", script = "Malayalam", hasCase = false,
        main = listOf(
            row(
                key("ക", listOf("൧")), key("ഖ", listOf("൨")), key("ഗ", listOf("൩")), key("ഘ", listOf("൪")),
                key("ങ", listOf("൫")), key("ച", listOf("൬")), key("ഛ", listOf("൭")), key("ജ", listOf("൮")),
                key("ഝ", listOf("൯", "ഞ")), key("ട", listOf("൦"))
            ),
            row(
                key("ഠ"), key("ഡ"), key("ഢ"), key("ണ"), key("ത"),
                key("ഥ"), key("ദ"), key("ധ"), key("ന")
            ),
            row(
                key("പ"), key("ഫ"), key("ബ"), key("ഭ"), key("മ"),
                key("യ"), key("ര"), key("റ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ല"), key("ള"), key("ഴ"), key("വ"), key("ശ", listOf("സ", "ഷ")), key("സ"), key("ഹ", listOf("ം", "ഃ")),
                key("അ", listOf("ആ", "ാ", "്", "ഽ")), key("ഇ", listOf("ഈ", "ീ", "ി")),
                key("ഉ", listOf("ഊ", "ൂ", "ു")), key("എ", listOf("ഏ", "െ", "േ", "ഐ", "ൈ", "ഒ", "ൊ", "ോ", "ഔ", "ൌ", "ഋ", "ൠ", "ൄ", "ൕ", "ൖ"))
            ),
            navRow("മലയാളം")
        )
    )
}
