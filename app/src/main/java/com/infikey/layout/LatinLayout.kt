package com.infikey.layout

object LatinLayout {

    private fun bottomRow(spaceLabel: String) = row(
        Key("?123", action = KeyAction.SYMBOLS, width = 1.3f),
        Key("\uD83C\uDF10", label = "\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
        Key(",", width = 1f),
        Key("space", label = spaceLabel, output = " ", action = KeyAction.SPACE, width = 4f),
        Key(".", width = 1f, popups = listOf("!", "?", ";", ":")),
        Key("⏎", action = KeyAction.ENTER, width = 1.5f)
    )

    val LAYOUT = Layout(
        id = Layouts.LATIN, name = "English (QWERTY)", script = "Latin",
        main = listOf(
            row(
                key("q", listOf("1")), key("w", listOf("2")), key("e", listOf("3", "é", "è")),
                key("r", listOf("4")), key("t", listOf("5")), key("y", listOf("6")),
                key("u", listOf("7", "ü")), key("i", listOf("8", "í")), key("o", listOf("9", "ó")),
                key("p", listOf("0"))
            ),
            row(
                key("a", listOf("á", "à", "â", "ä", "ã")), key("s", listOf("ß", "ś")),
                key("d"), key("f"), key("g"), key("h"), key("j"), key("k"), key("l", listOf("ł"))
            ),
            row(
                Key("\u21E7", action = KeyAction.SHIFT, width = 1.5f),
                key("z"), key("x"), key("c", listOf("ç")), key("v"), key("b"), key("n", listOf("ñ")),
                key("m"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            bottomRow("InfiKey")
        ),
        shifted = listOf(
            row(
                key("Q"), key("W"), key("E"), key("R"), key("T"), key("Y"),
                key("U"), key("I"), key("O"), key("P")
            ),
            row(key("A"), key("S"), key("D"), key("F"), key("G"), key("H"), key("J"), key("K"), key("L")),
            row(
                Key("\u21E7", action = KeyAction.SHIFT, width = 1.5f),
                key("Z"), key("X"), key("C"), key("V"), key("B"), key("N"), key("M"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            bottomRow("InfiKey")
        ),
        symbols = SymbolsLayout.PAGE1
    )
}

object SymbolsLayout {

    val PAGE1 = listOf(
        row(
            key("1"), key("2"), key("3"), key("4"), key("5"),
            key("6"), key("7"), key("8"), key("9"), key("0")
        ),
        row(
            key("@"), key("#"), key("$"), key("%"), key("&"),
            key("-"), key("+"), key("("), key(")")
        ),
        row(
            Key("=\\<", label = "=\\<", output = "more", action = KeyAction.SYMBOLS, width = 1.5f),
            key("*"), key("\""), key("'"), key(":"), key(";"), key("!"), key("?"),
            Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
        ),
        row(
            Key("ABC", label = "ABC", output = "abc", action = KeyAction.ALPHABET, width = 1.3f),
            Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
            Key("_", width = 1f),
            Key("space", label = "space", output = " ", action = KeyAction.SPACE, width = 4f),
            Key("/", width = 1f),
            Key("⏎", action = KeyAction.ENTER, width = 1.5f)
        )
    )

    val PAGE2 = listOf(
        row(
            key("~"), key("`"), key("|"), key("•"), key("√"),
            key("π"), key("÷"), key("×"), key("¶"), key("Δ")
        ),
        row(
            key("£"), key("€"), key("¥"), key("₹"), key("¢"),
            key("^"), key("®"), key("©"), key("™"), key("°")
        ),
        row(
            Key("?\\=", label = "?\\=", output = "more2", action = KeyAction.SYMBOLS, width = 1.5f),
            key("\\"), key("{"), key("}"), key("["), key("]"),
            key("<"), key(">"),
            Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
        ),
        row(
            Key("ABC", label = "ABC", output = "abc", action = KeyAction.ALPHABET, width = 1.3f),
            Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
            Key("=", width = 1f),
            Key("space", label = "space", output = " ", action = KeyAction.SPACE, width = 4f),
            Key("=", width = 1f),
            Key("⏎", action = KeyAction.ENTER, width = 1.5f)
        )
    )
}
