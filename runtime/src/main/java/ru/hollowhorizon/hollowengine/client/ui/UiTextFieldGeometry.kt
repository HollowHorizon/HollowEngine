package ru.hollowhorizon.hollowengine.client.ui

internal const val TextFieldCaretWidth = 1f
internal const val TextFieldCaretVisibilityPadding = 2f
private const val TextFieldHorizontalVisibilityFraction = 0.12f
private const val TextFieldLineNumberGap = 8f

internal fun textFieldEditLayout(node: TextFieldNode, style: ComputedStyle, layout: UiLayoutNode): UiTextLayout {
    val inlayStyle = UiInlineStyle().withColor(style.textField.inlayHintColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.55f))
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = node.syntaxHighlighter,
            inlayHints = if (style.textField.inlayHints == true) node.inlayHints else emptyList(),
            inlayStyle = inlayStyle,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
        wrap = textFieldWrap(style, node, constrainedWidth = true),
        align = style.textAlign,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

internal fun textFieldDisplayLayout(node: TextFieldNode, style: ComputedStyle, layout: UiLayoutNode): UiTextLayout {
    val inlayStyle = UiInlineStyle().withColor(style.textField.inlayHintColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.55f))
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = node.syntaxHighlighter,
            inlayHints = if (style.textField.inlayHints == true) node.inlayHints else emptyList(),
            inlayStyle = inlayStyle,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
        wrap = textFieldWrap(style, node, constrainedWidth = true),
        align = style.textAlign,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

internal fun textFieldTextOffset(node: TextFieldNode, style: ComputedStyle, layout: UiLayoutNode): Float {
    if (style.textField.lineNumbers != true || !node.multiline) return 0f
    val lines = node.value.count { it == '\n' } + 1
    val digits = lines.toString().length.coerceAtLeast(2)
    return digits * style.fontSize * 0.62f + TextFieldLineNumberGap
}

internal fun textFieldTextWidth(node: TextFieldNode, style: ComputedStyle, layout: UiLayoutNode): Float {
    return (layout.content.width - textFieldTextOffset(node, style, layout)).coerceAtLeast(1f)
}

internal fun textFieldHorizontalScrollPadding(viewportWidth: Float): Float {
    return maxOf(TextFieldCaretVisibilityPadding, viewportWidth * TextFieldHorizontalVisibilityFraction)
}

internal fun textFieldWrap(style: ComputedStyle, node: TextFieldNode, constrainedWidth: Boolean): Boolean {
    return style.textWrap && node.multiline && constrainedWidth
}

internal fun textFieldWidthConstrained(style: ComputedStyle, node: TextFieldNode, contentWidth: Float): Boolean {
    if (style.size.width !is UiLength.Auto || UiStyleProperty.WIDTH in style.explicitProperties) return true
    val text = node.value.ifEmpty { node.placeholder }
    val naturalWidth = UiTextLayouter.measure(
        text = text,
        availableWidth = Float.POSITIVE_INFINITY,
        knownWidth = null,
        wrap = false,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    ).width
    return contentWidth + 0.5f < naturalWidth
}
