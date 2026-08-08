package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiInsets
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiStyleProperty

private val AlignKeywords = listOf(
    "auto",
    "start",
    "center",
    "end",
    "stretch",
    "space-between",
    "space-around",
    "space-evenly",
    "justify",
)

private fun alignSlot(name: String) = HssSlot(name, HssValueKind.KEYWORD, keywords = AlignKeywords)

private fun lengthSyntax(name: String, auto: Boolean = true) = syntax(sizeSlot(name, auto))

/** Sizing, spacing, alignment and placement. */
internal fun layoutHssProperties(): List<HssProperty> = hssProperties {
    property(
        "size",
        summary = "Preferred size; one value sizes both axes.",
        syntax = axesSyntax(sizeSlot("width"), sizeSlot("height")),
        examples = listOf("fill fill", "fit fit", "64px 64px", "100% 32px"),
    ) { style(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT) { it.size = parseSize(value) } }

    property(
        "width",
        summary = "Preferred width.",
        syntax = lengthSyntax("width"),
        examples = listOf("fill", "fit", "100%", "64px"),
    ) { style(UiStyleProperty.WIDTH) { it.width = parseLength(value) } }

    property(
        "height",
        summary = "Preferred height.",
        syntax = lengthSyntax("height"),
        examples = listOf("fill", "fit", "100%", "64px"),
    ) { style(UiStyleProperty.HEIGHT) { it.height = parseLength(value) } }

    property(
        "min-size",
        summary = "Lower size bound; one value bounds both axes.",
        syntax = axesSyntax(sizeSlot("width"), sizeSlot("height")),
        examples = listOf("0px 0px", "64px 64px"),
    ) { style { it.minSize = parseSize(value) } }

    property(
        "min-width",
        summary = "Lower width bound.",
        syntax = lengthSyntax("width"),
    ) { style { it.minWidth = parseLength(value) } }

    property(
        "min-height",
        summary = "Lower height bound.",
        syntax = lengthSyntax("height"),
    ) { style { it.minHeight = parseLength(value) } }

    property(
        "max-size",
        summary = "Upper size bound; one value bounds both axes.",
        syntax = axesSyntax(sizeSlot("width"), sizeSlot("height")),
        examples = listOf("100% 100%", "auto auto"),
    ) { style { it.maxSize = parseSize(value) } }

    property(
        "max-width",
        summary = "Upper width bound.",
        syntax = lengthSyntax("width"),
    ) { style { it.maxWidth = parseLength(value) } }

    property(
        "max-height",
        summary = "Upper height bound.",
        syntax = lengthSyntax("height"),
    ) { style { it.maxHeight = parseLength(value) } }

    property(
        "aspect-ratio",
        summary = "Locks the height to the width by a ratio.",
        syntax = syntax(slot("ratio", HssValueKind.NUMBER)),
        examples = listOf("1", "16/9", "4/3"),
    ) { style { it.aspectRatio = parseAspectRatio(value) } }

    property(
        "padding",
        summary = "Inner spacing on all four edges.",
        syntax = edgesSyntax(auto = false),
        examples = listOf("8px", "8px 12px", "4px 8px 4px 8px"),
    ) { style { it.padding = parseInsets(value, allowAuto = false) } }

    edgeProperty("padding-left", "Inner spacing on the left edge.", allowAuto = false) { insets, length ->
        insets.copy(left = length)
    }
    edgeProperty("padding-top", "Inner spacing on the top edge.", allowAuto = false) { insets, length ->
        insets.copy(top = length)
    }
    edgeProperty("padding-right", "Inner spacing on the right edge.", allowAuto = false) { insets, length ->
        insets.copy(right = length)
    }
    edgeProperty("padding-bottom", "Inner spacing on the bottom edge.", allowAuto = false) { insets, length ->
        insets.copy(bottom = length)
    }
    edgeProperty(
        "padding-x",
        "Inner spacing on the left and right edges.",
        allowAuto = false,
        aliases = arrayOf("padding-horizontal", "padding-inline"),
    ) { insets, length -> insets.copy(left = length, right = length) }
    edgeProperty(
        "padding-y",
        "Inner spacing on the top and bottom edges.",
        allowAuto = false,
        aliases = arrayOf("padding-vertical", "padding-block"),
    ) { insets, length -> insets.copy(top = length, bottom = length) }

    property(
        "margin",
        summary = "Outer spacing on all four edges; `auto` centers along that axis.",
        syntax = edgesSyntax(auto = true),
        examples = listOf("8px", "8px 12px", "8px 8px 60px 8px", "auto"),
    ) { style { it.margin = parseInsets(value, allowAuto = true) } }

    edgeProperty("margin-left", "Outer spacing on the left edge.", allowAuto = true, margin = true) { insets, length ->
        insets.copy(left = length)
    }
    edgeProperty("margin-top", "Outer spacing on the top edge.", allowAuto = true, margin = true) { insets, length ->
        insets.copy(top = length)
    }
    edgeProperty("margin-right", "Outer spacing on the right edge.", allowAuto = true, margin = true) { insets, length ->
        insets.copy(right = length)
    }
    edgeProperty(
        "margin-bottom",
        "Outer spacing on the bottom edge.",
        allowAuto = true,
        margin = true,
    ) { insets, length -> insets.copy(bottom = length) }
    edgeProperty(
        "margin-x",
        "Outer spacing on the left and right edges.",
        allowAuto = true,
        margin = true,
        aliases = arrayOf("margin-horizontal", "margin-inline"),
    ) { insets, length -> insets.copy(left = length, right = length) }
    edgeProperty(
        "margin-y",
        "Outer spacing on the top and bottom edges.",
        allowAuto = true,
        margin = true,
        aliases = arrayOf("margin-vertical", "margin-block"),
    ) { insets, length -> insets.copy(top = length, bottom = length) }

    property(
        "gap",
        summary = "Spacing between children of a row or column.",
        syntax = lengthSyntax("gap"),
        examples = listOf("4px", "8px", "12px"),
    ) { style { it.gap = parseLength(value) } }

    property(
        "align",
        summary = "How this node aligns inside its parent; one value aligns both axes.",
        syntax = axesSyntax(alignSlot("horizontal"), alignSlot("vertical")),
        examples = listOf("center", "center center", "end end", "start center"),
    ) { style { applySelfAlignment(it, value) } }

    property(
        "align-items",
        summary = "How children align inside this node; one value aligns both axes.",
        syntax = axesSyntax(alignSlot("horizontal"), alignSlot("vertical")),
        examples = listOf("center", "stretch", "start center"),
    ) { style { applyChildAlignment(it, value) } }

    property(
        "align-x", "align-horizontal",
        summary = "Horizontal alignment inside the parent.",
        syntax = syntax(alignSlot("horizontal")),
    ) { style { it.alignHorizontal = parseAlign(value) } }

    property(
        "align-y", "align-vertical",
        summary = "Vertical alignment inside the parent.",
        syntax = syntax(alignSlot("vertical")),
    ) { style { it.alignVertical = parseAlign(value) } }

    property(
        "align-self",
        summary = "Alignment along the cross axis of the parent layout.",
        syntax = syntax(alignSlot("align")),
    ) { style { it.alignSelf = parseAlign(value) } }

    property(
        "justify-self",
        summary = "Alignment along the main axis of the parent layout.",
        syntax = syntax(alignSlot("align")),
    ) { style { it.justifySelf = parseAlign(value) } }

    property(
        "justify-content", "justify",
        summary = "How children are distributed along the main axis.",
        syntax = syntax(alignSlot("align")),
        examples = listOf("start", "center", "space-between", "space-evenly"),
    ) { style { it.justifyContent = parseAlign(value) } }

    property(
        "grow",
        summary = "Share of the leftover main-axis space this node takes.",
        syntax = syntax(slot("factor", HssValueKind.NUMBER)),
        examples = listOf("0", "1", "2"),
    ) { style { it.grow = value.toFloat() } }

    property(
        "position",
        summary = "Offset from the layout position; `z` orders siblings.",
        syntax = syntax(sizeSlot("x", auto = false), sizeSlot("y", auto = false), slot("z", HssValueKind.NUMBER, optional = true)),
        examples = listOf("0px 0px", "8px 16px", "0px 0px 10"),
    ) { style { it.position = parsePosition(value) } }

    property(
        "layer",
        summary = "Render layer; higher layers draw above lower ones.",
        syntax = syntax(slot("layer", HssValueKind.INTEGER)),
        examples = listOf("0", "1", "10"),
    ) { style { it.layer = value.toInt() } }
}

/**
 * Declares a single-edge shorthand of `padding`/`margin`; both keep whatever the other
 * edges already hold, so `padding: 8px; padding-top: 0px` behaves like CSS.
 */
private fun HssPropertyBuilder.edgeProperty(
    name: String,
    summary: String,
    allowAuto: Boolean,
    margin: Boolean = false,
    aliases: Array<String> = emptyArray(),
    patch: (UiInsets, UiLength) -> UiInsets,
) {
    property(
        name,
        *aliases,
        summary = summary,
        syntax = syntax(sizeSlot("length", allowAuto)),
        examples = listOf("0px", "4px", "8px"),
    ) {
        style {
            val length = parseLength(value, allowAuto)
            if (margin) {
                it.margin = patch(it.margin ?: UiInsets.Zero, length)
            } else {
                it.padding = patch(it.padding ?: UiInsets.Zero, length)
            }
        }
    }
}
