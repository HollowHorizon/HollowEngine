package ru.hollowhorizon.hollowengine.client.ui

internal const val TextFieldCaretWidth = 1f
internal const val TextFieldCaretVisibilityPadding = 2f

internal fun textFieldEditLayout(node: TextFieldNode, style: ComputedStyle, layout: UiLayoutNode): UiTextLayout {
    return UiTextLayouter.layout(
        text = node.value,
        width = layout.content.width,
        height = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
        wrap = textFieldWrap(style, node, constrainedWidth = true),
        align = style.textAlign,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
    )
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
    ).width
    return contentWidth + 0.5f < naturalWidth
}
