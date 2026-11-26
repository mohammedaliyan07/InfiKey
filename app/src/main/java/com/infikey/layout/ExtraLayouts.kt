package com.infikey.layout

/** Special-purpose layouts: DEV (developer), MARKDOWN, NUMPAD. */
object ExtraLayouts {

    val DEV = Layout(
        id = Layouts.DEV, name = "Developer", script = "Symbols", hasCase = true,
        main = listOf(
            row(
                key("{"), key("}"), key("["), key("]"), key("("),
                key(")"), key("<"), key(">"), key("=")
            ),
            row(
                key(";"), key(":"), key("!", listOf("|")), key("&", listOf("^")), key("|"),
                key("?", listOf("~")), key("`"), key("\\"), key("/")
            ),
            row(
                key("_"), key("$", listOf("€", "£", "¥")), key("#", listOf("%", "@")),
                key("\""), key("'"), key("→", listOf("←")), key("×"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                Key("ABC", output = "abc", action = KeyAction.ALPHABET, width = 1.3f),
                Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
                Key(",", width = 1f),
                Key("space", output = " ", action = KeyAction.SPACE, width = 4f),
                Key(".", width = 1f),
                Key("⏎", action = KeyAction.ENTER, width = 1.5f)
            )
        )
    )

    val MARKDOWN = Layout(
        id = Layouts.MARKDOWN, name = "Markdown", script = "Symbols", hasCase = true,
        main = listOf(
            row(
                key("#", listOf("##", "###")), key("*", listOf("**", "***")), key("_", listOf("__")),
                key("`", listOf("```")), key("~", listOf("~~")), key(">"), key("|")
            ),
            row(
                key("[", listOf("{")), key("]"), key("(", listOf("<")), key(")"), key("!"),
                key("-", listOf("–", "—")), key("+"), key("=", listOf("=="))
            ),
            row(
                Key("1.", output = "1. "), Key("- [ ]", output = "- [ ] "),
                Key("```", output = "```\n\n```"), Key("> ", output = "> "),
                Key("|---|", output = "| --- |"),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                Key("ABC", output = "abc", action = KeyAction.ALPHABET, width = 1.3f),
                Key("\uD83C\uDF10", output = "globe", action = KeyAction.GLOBE, width = 1f),
                Key(",", width = 1f),
                Key("space", output = " ", action = KeyAction.SPACE, width = 4f),
                Key(".", width = 1f),
                Key("⏎", action = KeyAction.ENTER, width = 1.5f)
            )
        )
    )

    val NUMPAD = Layout(
        id = Layouts.NUMPAD, name = "Number pad", script = "Numeric", hasCase = false,
        main = listOf(
            row(
                key("7"), key("8"), key("9"), key("/", listOf("÷")),
                Key("⌫", action = KeyAction.BACKSPACE, width = 1.5f)
            ),
            row(
                key("4"), key("5"), key("6"), key("*", listOf("×")),
                Key("C", output = "clear", action = KeyAction.NONE, width = 1.5f)
            ),
            row(
                key("1"), key("2"), key("3"), key("-", listOf("−")),
                Key("=", output = "=", width = 1.5f)
            ),
            row(
                Key("ABC", output = "abc", action = KeyAction.ALPHABET, width = 1.3f),
                key("0"), key(".", listOf(",")),
                Key("space", output = " ", action = KeyAction.SPACE, width = 2f),
                Key("⏎", action = KeyAction.ENTER, width = 1.5f)
            )
        )
    )
}
