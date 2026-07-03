package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import kotlin.math.abs

private const val ConstraintReflowEpsilon = 0.01f

internal fun UiLayoutPipeline.rootRect(
    resolved: UiNode,
    width: Float,
    height: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
): UiRect {
    val node = resolved.root
    val style = resolved[node]
    val margin = style.margin.resolve(width, height)
    val availableWidth = (width - margin.left - margin.right).coerceAtLeast(0f)
    val availableHeight = (height - margin.top - margin.bottom).coerceAtLeast(0f)
    val measured =
        measureNode(node, resolved, availableWidth, availableHeight, scrollbarReserves)
    val rootWidth = if (style.size.width is UiLength.Auto && UiStyleProperty.WIDTH !in style.explicitProperties) {
        availableWidth
    } else {
        measured.width
    }
    val rootHeight =
        if (style.size.height is UiLength.Auto && UiStyleProperty.HEIGHT !in style.explicitProperties) {
            availableHeight
        } else {
            measured.height
        }
    val alignX = style.alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: UiAlign.START
    val alignY = style.alignVertical.takeUnless { it == UiAlign.AUTO } ?: UiAlign.START
    return UiRect(
        x = alignX.crossOffset(width, rootWidth, margin.left, margin.right),
        y = alignY.crossOffset(height, rootHeight, margin.top, margin.bottom),
        width = rootWidth.coerceIn(style.minSize.width, style.maxSize.width, width),
        height = rootHeight.coerceIn(style.minSize.height, style.maxSize.height, height),
    )
}


internal fun UiLayoutPipeline.measureNode(
    node: UiNode,
    resolved: UiNode,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    widthOverride: Float? = null,
    heightOverride: Float? = null,
    deferFlexibleWidth: Boolean = false,
    deferFlexibleHeight: Boolean = false,
    allowWidthOverflow: Boolean = false,
    allowHeightOverflow: Boolean = false,
): LayoutSize = measureNodeContent(
    node,
    resolved,
    availableWidth,
    availableHeight,
    scrollbarReserves,
    widthOverride,
    heightOverride,
    deferFlexibleWidth,
    deferFlexibleHeight,
    allowWidthOverflow,
    allowHeightOverflow,
)

internal fun UiLayoutPipeline.measureNodeContent(
    node: UiNode,
    resolved: UiNode,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    widthOverride: Float? = null,
    heightOverride: Float? = null,
    deferFlexibleWidth: Boolean = false,
    deferFlexibleHeight: Boolean = false,
    allowWidthOverflow: Boolean = false,
    allowHeightOverflow: Boolean = false,
): LayoutSize {
    val style = resolved[node]
    val referenceWidth = availableWidth.coerceAtLeast(0f)
    val referenceHeight = availableHeight.coerceAtLeast(0f)
    val insets =
        style.outerInsets(referenceWidth, referenceHeight, scrollbarReserves[node] ?: UiScrollbarReserve.None)
    var width = widthOverride ?: style.size.width.resolveOrNull(referenceWidth, deferFlexibleWidth)
    var height = heightOverride ?: style.size.height.resolveOrNull(referenceHeight, deferFlexibleHeight)
    style.aspectRatio?.let { ratio ->
        if (width == null && height != null) width = height * ratio
        if (height == null && width != null) height = width / ratio
    }
    if (width != null && height != null) {
        return constrainMeasuredSize(
            width = width,
            height = height,
            style = style,
            referenceWidth = referenceWidth,
            referenceHeight = referenceHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
        )
    }
    val childAvailableWidth = ((width ?: referenceWidth) - insets.horizontal).coerceAtLeast(0f)
    val childAvailableHeight = ((height ?: referenceHeight) - insets.vertical).coerceAtLeast(0f)
    val intrinsic = intrinsicSize(
        node,
        resolved,
        style,
        childAvailableWidth,
        childAvailableHeight,
        scrollbarReserves,
        knownContentWidth = width?.let { (it - insets.horizontal).coerceAtLeast(0f) },
        knownContentHeight = height?.let { (it - insets.vertical).coerceAtLeast(0f) },
    )
    width = width ?: (intrinsic.width + insets.horizontal)
    height = height ?: (intrinsic.height + insets.vertical)
    style.aspectRatio?.let { ratio ->
        val resolvedWidth = width
        val resolvedHeight = height
        if (style.size.width is UiLength.Auto && style.size.height !is UiLength.Auto) {
            width = resolvedHeight * ratio
        }
        if (style.size.height is UiLength.Auto && style.size.width !is UiLength.Auto) {
            height = resolvedWidth / ratio
        }
    }
    val finalWidth = requireNotNull(width)
    val finalHeight = requireNotNull(height)
    val widthReference =
        if (allowWidthOverflow && style.size.width is UiLength.Auto) Float.POSITIVE_INFINITY else referenceWidth
    val heightReference =
        if (allowHeightOverflow && style.size.height is UiLength.Auto) Float.POSITIVE_INFINITY else referenceHeight
    var constrainedWidth = finalWidth.coerceIn(style.minSize.width, style.maxSize.width, widthReference)
    var constrainedHeight = finalHeight.coerceIn(style.minSize.height, style.maxSize.height, heightReference)
    val widthConstrained = abs(constrainedWidth - finalWidth) > ConstraintReflowEpsilon
    val shouldReflowConstrainedWidth =
        widthConstrained &&
                (node is TextNode ||
                        heightOverride == null && style.size.height is UiLength.Auto ||
                        widthOverride == null && style.size.width is UiLength.Auto)

    if (!shouldReflowConstrainedWidth) return LayoutSize(constrainedWidth, constrainedHeight)

    val constrainedContentWidth = (constrainedWidth - insets.horizontal).coerceAtLeast(0f)
    val reflowed = intrinsicSize(
        node,
        resolved,
        style,
        constrainedContentWidth,
        childAvailableHeight,
        scrollbarReserves,
        knownContentWidth = constrainedContentWidth,
        knownContentHeight = null,
    )
    if (widthOverride == null && style.size.width is UiLength.Auto) {
        constrainedWidth = (reflowed.width + insets.horizontal)
            .coerceIn(style.minSize.width, style.maxSize.width, widthReference)
    }
    if (heightOverride == null && style.size.height is UiLength.Auto) {
        constrainedHeight = (reflowed.height + insets.vertical)
            .coerceIn(style.minSize.height, style.maxSize.height, heightReference)
    }

    return LayoutSize(
        width = constrainedWidth,
        height = constrainedHeight,
    )
}

private fun constrainMeasuredSize(
    width: Float,
    height: Float,
    style: UiComputedStyle,
    referenceWidth: Float,
    referenceHeight: Float,
    allowWidthOverflow: Boolean,
    allowHeightOverflow: Boolean,
): LayoutSize {
    val widthReference =
        if (allowWidthOverflow && style.size.width is UiLength.Auto) Float.POSITIVE_INFINITY else referenceWidth
    val heightReference =
        if (allowHeightOverflow && style.size.height is UiLength.Auto) Float.POSITIVE_INFINITY else referenceHeight
    return LayoutSize(
        width = width.coerceIn(style.minSize.width, style.maxSize.width, widthReference),
        height = height.coerceIn(style.minSize.height, style.maxSize.height, heightReference),
    )
}

private fun UiLayoutPipeline.intrinsicSize(
    node: UiNode,
    resolved: UiNode,
    style: UiComputedStyle,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    knownContentWidth: Float? = null,
    knownContentHeight: Float? = null,
): LayoutSize {
    return when (node) {
        is TextNode -> measureTextNode(
            node,
            resolved,
            style,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            knownContentWidth,
        )

        is TextFieldNode -> measureTextFieldNode(
            node,
            resolved,
            style,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            knownContentWidth,
        )
        else -> {
            if (layoutChildren(node).isEmpty()) return replacedIntrinsicSize(node, style)

            if (node.measurePolicy !is UiBuiltInMeasurePolicy) {
                measureCustomContainer(
                    node,
                    resolved,
                    node.measurePolicy,
                    availableWidth,
                    availableHeight,
                    scrollbarReserves,
                )
            } else {
                measureStandardContainer(node, resolved, style, availableWidth, availableHeight, scrollbarReserves, knownContentWidth, knownContentHeight)
            }
        }
    }
}

private fun UiLayoutPipeline.measureTextNode(
    node: TextNode,
    resolved: UiNode,
    style: UiComputedStyle,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    knownContentWidth: Float?,
): LayoutSize {
    val widgetMetrics = measureInlineWidgetMetrics(
        node, resolved, availableWidth, availableHeight, scrollbarReserves
    )
    return UiTextLayouter.measure(
        richText = node.content.toRichText(widgetMetrics),
        availableWidth = availableWidth,
        knownWidth = knownContentWidth,
        wrap = style.textWrap,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

private fun UiLayoutPipeline.measureTextFieldNode(
    node: TextFieldNode,
    resolved: UiNode,
    style: UiComputedStyle,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    knownContentWidth: Float?,
): LayoutSize {
    val widgetMetrics = measureInlineWidgetMetrics(
        node,
        resolved,
        availableWidth,
        availableHeight,
        scrollbarReserves,
    )
    val layout = temporaryTextFieldLayoutNode(
        node,
        UiRect(0f, 0f, availableWidth, availableHeight),
    )
    val textOffset = textFieldTextOffset(node, style)
    val textWidth = (availableWidth - textOffset).coerceAtLeast(1f)
    val knownTextWidth = knownContentWidth?.let { (it - textOffset).coerceAtLeast(1f) }
    val measured = UiTextLayouter.measure(
        richText = node.value.ifEmpty { node.placeholder }.toHighlightedRichText(
            highlighter = null,
            inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList(),
            inlayStyle = textFieldInlayStyle(style),
            inlayWidgetMetrics = widgetMetrics,
        ),
        availableWidth = textWidth,
        knownWidth = knownTextWidth,
        wrap = textFieldWrap(style, node, knownTextWidth != null),
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        preserveWhitespace = true,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
    return if (knownContentWidth == null) {
        measured.copy(width = textOffset + measured.width + TextFieldCaretWidth + TextFieldCaretVisibilityPadding)
    } else {
        measured
    }
}

private fun UiLayoutPipeline.measureCustomContainer(
    node: UiNode,
    resolved: UiNode,
    measurePolicy: UiMeasurePolicy,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
): LayoutSize {
    val result = measureCustomLayout(node, resolved, measurePolicy, availableWidth, availableHeight, scrollbarReserves)
    return LayoutSize(result.width, result.height)
}

private fun UiLayoutPipeline.measureStandardContainer(
    node: UiNode,
    resolved: UiNode,
    style: UiComputedStyle,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    knownContentWidth: Float?,
    knownContentHeight: Float?,
): LayoutSize {
    val children = measureFlowChildren(
        node,
        resolved,
        availableWidth,
        availableHeight,
        scrollbarReserves,
        deferFlexibleWidth = style.size.width is UiLength.Auto,
        deferFlexibleHeight = style.size.height is UiLength.Auto,
        allowWidthOverflow = style.input.scrollable,
        allowHeightOverflow = style.input.scrollable,
    )

    val layoutAxis = node.measurePolicy.flowAxis()
    val isHorizontal = layoutAxis == FlowAxis.Horizontal
    val gap = style.gap.resolve(if (isHorizontal) availableWidth else availableHeight)

    return node.measurePolicy.policy().intrinsic(
        this,
        ChildIntrinsicScope(
            children = children,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            knownContentWidth = knownContentWidth,
            knownContentHeight = knownContentHeight,
            gap = gap,
            resolved = resolved,
            scrollbarReserves = scrollbarReserves,
        )
    )
}

internal fun nodeBoxes(rect: UiRect, style: UiComputedStyle, reserve: UiScrollbarReserve): NodeBoxes {
    val border = style.border.width.resolve(rect.width, rect.height)
    val padding = style.padding.resolve(rect.width, rect.height)
    val verticalScrollbar = style.scrollbar.resolved(rect.width)
    val horizontalScrollbar = style.scrollbar.resolved(rect.height)
    val scrollArea = UiRect(
        rect.x + border.left + padding.left,
        rect.y + border.top + padding.top,
        (rect.width - border.left - border.right - padding.left - padding.right).coerceAtLeast(0f),
        (rect.height - border.top - border.bottom - padding.top - padding.bottom).coerceAtLeast(0f),
    )
    return NodeBoxes(
        scrollArea = scrollArea,
        content = scrollArea.copy(
            width = (scrollArea.width - if (reserve.vertical) verticalScrollbar.gutter else 0f).coerceAtLeast(0f),
            height = (scrollArea.height - if (reserve.horizontal) horizontalScrollbar.gutter else 0f).coerceAtLeast(
                0f
            ),
        )
    )
}

internal fun UiLayoutPipeline.measureInlineWidgetMetrics(
    node: UiNode,
    resolved: UiNode,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
): Map<String, UiInlineWidgetMetrics> {
    val children = layoutChildren(node)
    if (children.isEmpty()) return emptyMap()
    val metrics = LinkedHashMap<String, UiInlineWidgetMetrics>()
    for (child in children) {
        val id = child.id ?: continue
        val margin = resolved[child].margin.resolve(availableWidth, availableHeight)
        val size =
            measureNode(child, resolved, availableWidth, availableHeight, scrollbarReserves)
        metrics[id] = UiInlineWidgetMetrics(size.width + margin.horizontal, size.height + margin.vertical)
    }
    return metrics
}

internal fun temporaryTextFieldLayoutNode(node: TextFieldNode, content: UiRect): UiLayoutNode {
    return UiLayoutNode(
        node = node,
        rect = content,
        content = content,
        clip = null,
        worldTransform = UiMatrix4.identity(),
        inputTransform = UiMatrix4.identity(),
        needsFramebuffer = false,
    )
}
