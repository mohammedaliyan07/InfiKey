package com.infikey.layout

/** Devanagari (Hindi/Marathi/Nepali), Gujarati, Gurmukhi (Punjabi), Odia. */
object NorthIndicLayouts {

    private fun navRow(spaceLabel: String) = row(
        Key("?123", action = KeyAction.SYMBOLS, width = 1.3f),
        Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
        Key(",", width = 1f),
        Key(label = spaceLabel, output = " ", action = KeyAction.SPACE, width = 4.4f),
        Key(".", width = 1f, popups = listOf("?", "!")),
        Key("⏎", action = KeyAction.ENTER, width = 1.5f)
    )

    val DEVANAGARI = Layout(
        id = Layouts.DEVANAGARI, name = "देवनागरी (Hindi)", script = "Devanagari", hasCase = false,
        main = listOf(
            row(
                key("क", listOf("१")), key("ख", listOf("२")), key("ग", listOf("३")), key("घ", listOf("४")),
                key("ङ", listOf("५")), key("च", listOf("६")), key("छ", listOf("७")), key("ज", listOf("८")),
                key("झ", listOf("९", "ञ")), key("ट", listOf("०"))
            ),
            row(
                key("ठ"), key("ड"), key("ढ"), key("ण"), key("त"),
                key("थ"), key("द"), key("ध"), key("न")
            ),
            row(
                key("प"), key("फ"), key("ब", listOf("़")), key("भ"), key("म"),
                key("य"), key("र"), key("ल"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("व"), key("श", listOf("स", "ष")), key("स"), key("ह", listOf("ं", "ः")),
                key("अ", listOf("आ", "ा", "्")), key("इ", listOf("ई", "ी", "ि")), key("उ", listOf("ऊ", "ू", "ु")),
                key("ए", listOf("ऐ", "ै", "े", "ओ", "ो", "औ", "ौ", "ऋ", "ृ"))
            ),
            navRow("हिंदी")
        )
    )

    val GUJARATI = Layout(
        id = Layouts.GUJARATI, name = "ગુજરાતી", script = "Gujarati", hasCase = false,
        main = listOf(
            row(
                key("ક", listOf("૧")), key("ખ", listOf("૨")), key("ગ", listOf("૩")), key("ઘ", listOf("૪")),
                key("ઙ", listOf("૫")), key("ચ", listOf("૬")), key("છ", listOf("૭")), key("જ", listOf("૮")),
                key("ઝ", listOf("૯", "ઞ")), key("ટ", listOf("૦"))
            ),
            row(
                key("ઠ"), key("ડ"), key("ઢ"), key("ણ"), key("ત"),
                key("થ"), key("દ"), key("ધ"), key("ન")
            ),
            row(
                key("પ"), key("ફ"), key("બ", listOf("઼")), key("ભ"), key("મ"),
                key("ય"), key("ર"), key("લ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("વ"), key("શ", listOf("સ", "ષ")), key("સ", listOf("ળ")), key("હ", listOf("ં", "ઃ")),
                key("અ", listOf("આ", "ા", "્")), key("ઇ", listOf("ઈ", "ી", "િ")), key("ઉ", listOf("ઊ", "ૂ", "ુ")),
                key("એ", listOf("ઐ", "ૈ", "ે", "ઓ", "ો", "ઔ", "ૌ", "ઍ", "ઑ", "ઽ"))
            ),
            navRow("ગુજરાતી")
        )
    )

    val GURMUKHI = Layout(
        id = Layouts.GURMUKHI, name = "ਪੰਜਾਬੀ", script = "Gurmukhi", hasCase = false,
        main = listOf(
            row(
                key("ਕ", listOf("੧")), key("ਖ", listOf("੨")), key("ਗ", listOf("੩")), key("ਘ", listOf("੪")),
                key("ਙ", listOf("੫")), key("ਚ", listOf("੬")), key("ਛ", listOf("੭")), key("ਜ", listOf("੮")),
                key("ਝ", listOf("੯", "ਞ")), key("ਟ", listOf("੦"))
            ),
            row(
                key("ਠ"), key("ਡ"), key("ਢ"), key("ਣ"), key("ਤ"),
                key("ਥ"), key("ਦ"), key("ਧ"), key("ਨ")
            ),
            row(
                key("ਪ"), key("ਫ"), key("ਬ", listOf("਼")), key("ਭ"), key("ਮ"),
                key("ਯ"), key("ਰ"), key("ਲ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ਲ਼"), key("ਵ"), key("ਸ", listOf("ਸ਼")), key("ਹ", listOf("ਂ", "ਃ")),
                key("ਅ", listOf("ਆ", "ਾ", "੍", "ੴ")), key("ਇ", listOf("ਈ", "ੀ", "ਿ")), key("ਉ", listOf("ਊ", "ੂ", "ੁ")),
                key("ਏ", listOf("ਐ", "ੈ", "ੇ", "ਓ", "ੋ", "ਔ", "ੌ", "ੜ"))
            ),
            navRow("ਪੰਜਾਬੀ")
        )
    )

    val ODIA = Layout(
        id = Layouts.ODIA, name = "ଓଡ଼ିଆ", script = "Odia", hasCase = false,
        main = listOf(
            row(
                key("କ", listOf("୧")), key("ଖ", listOf("୨")), key("ଗ", listOf("୩")), key("ଘ", listOf("୪")),
                key("ଙ", listOf("୫")), key("ଚ", listOf("୬")), key("ଛ", listOf("୭")), key("ଜ", listOf("୮")),
                key("ଝ", listOf("୯", "ଞ")), key("ଟ", listOf("୦"))
            ),
            row(
                key("ଠ"), key("ଡ", listOf("଼")), key("ଢ", listOf("଼")), key("ଣ"), key("ତ"),
                key("ଥ"), key("ଦ"), key("ଧ"), key("ନ")
            ),
            row(
                key("ପ"), key("ଫ"), key("ବ"), key("ଭ"), key("ମ"),
                key("ୟ"), key("ର"), key("ଲ"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("ଳ"), key("ୱ"), key("ଶ", listOf("ସ", "ଷ")), key("ହ", listOf("ଂ", "ଃ")),
                key("ଅ", listOf("ଆ", "ା", "୍", "ଽ")), key("ଇ", listOf("ଈ", "ୀ", "ି")), key("ଉ", listOf("ଊ", "ୂ", "ୁ")),
                key("ଏ", listOf("ଐ", "ୈ", "େ", "ଓ", "ୋ", "ଔ", "ୌ", "ୟ", "ଁ"))
            ),
            navRow("ଓଡ଼ିଆ")
        )
    )
}
