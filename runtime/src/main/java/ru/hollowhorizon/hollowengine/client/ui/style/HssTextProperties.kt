package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiBoxDecorationBreak
import ru.hollowhorizon.hollowengine.client.ui.text.Shadow
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextFieldStyle

private fun colorSyntax(name: String) = syntax(slot(name, HssValueKind.COLOR))

private fun booleanSyntax(name: String) = syntax(slot(name, HssValueKind.BOOLEAN))

/** Text layout, fonts, effects and the text-field chrome. */
internal fun textHssProperties(): List<HssProperty> = hssProperties {
    property(
        "text-wrap", "wrap",
        summary = "Whether text wraps onto the next line.",
        syntax = syntax(keywordSlot("wrap", "wrap", "nowrap")),
    ) { style { it.textWrap = parseTextWrap(value) } }

    property(
        "text-overflow",
        summary = "What happens to text that does not fit.",
        syntax = syntax(keywordSlot("overflow", "show", "hidden", "dots")),
    ) { style { it.textOverflow = parseTextOverflow(value) } }

    property(
        "box-decoration-break",
        summary = "Whether background and border repeat on every wrapped line fragment.",
        syntax = syntax(keywordSlot("break", *enumKeywords<UiBoxDecorationBreak>().toTypedArray())),
    ) { style { it.boxDecorationBreak = parseBoxDecorationBreak(value) } }

    property(
        "text-align",
        summary = "Horizontal alignment of text inside the node; inherited by children.",
        syntax = syntax(keywordSlot("align", "left", "center", "right", "justify")),
    ) { style { it.textAlign = parseTextAlign(value) } }

    property(
        "white-space", "whitespace",
        summary = "Whether runs of whitespace collapse or are preserved.",
        syntax = syntax(keywordSlot("whitespace", "normal", "pre")),
    ) { style { it.whitespace = parseWhitespace(value) } }

    property(
        "line-spacing", "text-line-spacing", "leading",
        summary = "Extra space between text lines.",
        syntax = syntax(slot("spacing", HssValueKind.PIXELS)),
        examples = listOf("0", "2px", "4px"),
    ) { style { it.lineSpacing = parseScalar(value).coerceAtLeast(0f) } }

    property(
        "space-width", "text-space-width",
        summary = "Width of the space character; overrides the font metric.",
        syntax = syntax(slot("width", HssValueKind.PIXELS)),
    ) { style { it.spaceWidth = parseScalar(value).coerceAtLeast(0f) } }

    property(
        "font-size",
        summary = "Font size; inherited by children. A share (`85%`, `0.85em`) follows the text around it.",
        syntax = syntax(HssSlot("size", HssValueKind.LENGTH, keywords = listOf("85%", "1em"))),
        examples = listOf("10px", "12px", "16px", "85%", "1.2em"),
    ) { style { it.fontSize = parseFontSize(value) } }

    property(
        "font-family",
        summary = "Font used for text; inherited by children. Defaults to `vanilla`, Minecraft's own " +
                "sheets. Also takes an MSDF atlas asset path or `ttf:<file>[?size=&range=&charset=]`.",
        syntax = syntax(slot("family", HssValueKind.TEXT)),
        examples = listOf(
            "\"vanilla\"",
            "\"vanilla:minecraft:alt\"",
            "\"hollowengine:fonts/monocraft\"",
            "\"ttf:hollowengine:fonts/inter.ttf?size=48&charset=latin+cyrillic\"",
        ),
    ) { style { it.fontFamily = unquote(value) } }

    property(
        "text-effects", "text-effect",
        summary = "Inline text effects applied to the whole node; inherited by children.",
        syntax = listSyntax(HssSlot("effect", HssValueKind.TEXT_EFFECT, keywords = UiTextEffectNames)),
        examples = listOf(
            "bold",
            "bold(0.12)",
            "italic, underline",
            "italic(18)",
            "underline(0.08, 0.02, #FF5555)",
            "gradient(#FF0000, #0000FF)",
            "none",
        ),
    ) { style { it.textEffects = parseTextEffects(value) } }

    property(
        "caret-color", "text-field-caret",
        summary = "Colour of the text-field caret.",
        syntax = colorSyntax("color"),
    ) { style { it.textField = it.textFieldStyle().copy(caretColor = parseColor(value)) } }

    property(
        "selection-color", "text-selection",
        summary = "Colour of the text-field selection highlight.",
        syntax = colorSyntax("color"),
    ) { style { it.textField = it.textFieldStyle().copy(selectionColor = parseColor(value)) } }

    property(
        "text-shadow", "text-field-shadow",
        summary = "Shadow drawn under text-field glyphs.",
        syntax = commaSyntax(
            slot("x", HssValueKind.NUMBER),
            slot("y", HssValueKind.NUMBER),
            slot("blur", HssValueKind.NUMBER, optional = true),
            slot("color", HssValueKind.COLOR, optional = true),
        ),
        examples = listOf("none", "1, 1, 0, #000000", "shadow(1, 1, 0, #00000099)"),
    ) { style { it.textField = it.textFieldStyle().copy(textShadow = parseFieldShadow(value), textShadowSet = true) } }

    property(
        "line-number-color",
        summary = "Colour of the code-editor line numbers.",
        syntax = colorSyntax("color"),
    ) { style { it.textField = it.textFieldStyle().copy(lineNumberColor = parseColor(value)) } }

    property(
        "line-numbers",
        summary = "Whether the code editor shows line numbers.",
        syntax = booleanSyntax("enabled"),
    ) { style { it.textField = it.textFieldStyle().copy(lineNumbers = parseBoolean(value)) } }

    property(
        "inlay-hints",
        summary = "Whether the code editor shows inlay hints.",
        syntax = booleanSyntax("enabled"),
    ) { style { it.textField = it.textFieldStyle().copy(inlayHints = parseBoolean(value)) } }
}

private fun UiStylePatch.textFieldStyle(): UiTextFieldStyle = textField ?: UiTextFieldStyle()

private fun parseFieldShadow(value: String): Shadow? {
    val trimmed = value.trim()
    val disabled = trimmed.equals("none", true) || trimmed.equals("false", true) || trimmed.equals("off", true)
    if (disabled) return null
    val effect = if (trimmed.startsWith("shadow(")) parseTextEffect(trimmed) else parseTextEffect("shadow($trimmed)")
    return effect as? Shadow
}
