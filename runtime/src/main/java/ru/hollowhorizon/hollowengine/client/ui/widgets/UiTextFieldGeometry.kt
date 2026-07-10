package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiStyleProperty
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter

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
    val inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList()
    val inlayStyle = if (inlayHints.isEmpty()) UiInlineStyle.Empty else textFieldInlayStyle(style)
    val fontSize = style.fontSize
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = null,
            inlayHints = inlayHints,
            inlayStyle = inlayStyle,
            inlayWidgetMetrics = inlayWidgetMetrics,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
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
    val inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList()
    val inlayStyle = if (inlayHints.isEmpty()) UiInlineStyle.Empty else textFieldInlayStyle(style)
    val fontSize = style.fontSize
    return UiTextLayouter.layout(
        richText = node.value.toHighlightedRichText(
            highlighter = node.syntaxHighlighter,
            caret = node.caret,
            inlayHints = inlayHints,
            inlayStyle = inlayStyle,
            inlayWidgetMetrics = inlayWidgetMetrics,
        ),
        width = textFieldTextWidth(node, style, layout),
        height = if (style.scrollable) Float.POSITIVE_INFINITY else layout.content.height,
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
    return UiInlineStyle.Empty.withColor(
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
