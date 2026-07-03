package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.style.*

internal const val TextFieldCaretWidth = 1f
internal const val TextFieldCaretVisibilityPadding = 2f
private const val TextFieldHorizontalVisibilityFraction = 0.12f
private const val TextFieldLineNumberGap = 8f

internal fun textFieldEditLayout(
    node: TextFieldNode,
    style: UiComputedStyle,
    layout: UiLayoutNode,
    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap(),
): UiTextLayout {
    val inlayStyle = textFieldInlayStyle(style)
    val fontSize = style.fontSize
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = null,
            inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList(),
            inlayStyle = inlayStyle,
            inlayWidgetMetrics = inlayWidgetMetrics,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
        wrap = textFieldWrap(style, node, constrainedWidth = true),
        align = style.textAlign,
        fontSize = fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

internal fun textFieldDisplayLayout(
    node: TextFieldNode,
    style: UiComputedStyle,
    layout: UiLayoutNode,
    inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics> = emptyMap(),
): UiTextLayout {
    val inlayStyle = textFieldInlayStyle(style)
    val fontSize = style.fontSize
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = node.syntaxHighlighter?.forCaret(node.caret),
            inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList(),
            inlayStyle = inlayStyle,
            inlayWidgetMetrics = inlayWidgetMetrics,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
        wrap = textFieldWrap(style, node, constrainedWidth = true),
        align = style.textAlign,
        fontSize = fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

internal fun textFieldTextOffset(node: TextFieldNode, style: UiComputedStyle): Float {
    if (style.textField.lineNumbers != true || !node.multiline) return 0f
    val lines = node.value.count { it == '\n' } + 1
    val digits = lines.toString().length.coerceAtLeast(2)
    return digits * style.fontSize * 0.62f + TextFieldLineNumberGap
}

internal fun textFieldInlayStyle(style: UiComputedStyle): UiInlineStyle {
    return UiInlineStyle().withColor(
        (style.textField.inlayHintColor ?: UiColor(0.66f, 0.72f, 0.82f, 1f)).copy(alpha = 0.95f)
    )
}

internal fun textFieldTextWidth(node: TextFieldNode, style: UiComputedStyle, layout: UiLayoutNode): Float {
    return (layout.content.width - textFieldTextOffset(node, style)).coerceAtLeast(1f)
}

internal fun textFieldHorizontalScrollPadding(viewportWidth: Float): Float {
    return maxOf(TextFieldCaretVisibilityPadding, viewportWidth * TextFieldHorizontalVisibilityFraction)
}

internal fun textFieldWrap(style: UiComputedStyle, node: TextFieldNode, constrainedWidth: Boolean): Boolean {
    return style.textWrap && node.multiline && constrainedWidth
}

internal fun textFieldWidthConstrained(style: UiComputedStyle, node: TextFieldNode, contentWidth: Float): Boolean {
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

private fun UiSyntaxHighlighter.forCaret(caret: Int): UiSyntaxHighlighter {
    if (this !is UiCaretAwareSyntaxHighlighter) return this
    return UiSyntaxHighlighter { text -> highlight(text, caret) }
}
