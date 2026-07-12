package ru.hollowhorizon.hollowengine.client.ui.layout


import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import kotlin.math.abs

private const val DirectTextTransformEpsilon = 0.0001f
internal val UiLength.dependsOnAvailableSpace: Boolean
    get() = when (this) {
        UiLength.Auto, UiLength.Fill, is UiLength.Percent -> true
        UiLength.Fit, is UiLength.Px -> false
        is UiLength.Addition -> first.dependsOnAvailableSpace || second.dependsOnAvailableSpace
        is UiLength.Substraction -> first.dependsOnAvailableSpace || second.dependsOnAvailableSpace
    }


internal fun UiLength.resolveWidth(
    align: UiAlign,
    childStyle: UiComputedStyle,
    child: MeasuredChild,
    content: UiRect,
): Float {
    return when (this) {
        UiLength.Auto -> if (align == UiAlign.STRETCH && child.canStretchAutoWidth) {
            (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
                .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)
        } else {
            child.size.width
        }

        UiLength.Fit -> child.size.width

        UiLength.Fill -> (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Percent -> ((content.width - child.margin.left - child.margin.right).coerceAtLeast(0f) * value)
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Px -> child.size.width
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Addition -> first.resolveWidth(align, childStyle, child, content) + second.resolveWidth(
            align,
            childStyle,
            child,
            content
        )

        is UiLength.Substraction -> first.resolveWidth(align, childStyle, child, content) - second.resolveWidth(
            align,
            childStyle,
            child,
            content
        )
    }
}

internal fun UiLength.resolveHeight(
    align: UiAlign,
    childStyle: UiComputedStyle,
    child: MeasuredChild,
    content: UiRect,
): Float {
    return when (this) {
        UiLength.Auto -> if (align == UiAlign.STRETCH || childStyle.scrollable && child.node.isInlineFlow()) {
            (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
                .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)
        } else {
            child.size.height
        }

        UiLength.Fit -> child.size.height

        UiLength.Fill -> (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Percent -> ((content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f) * value)
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Px -> child.size.height
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Addition -> first.resolveHeight(align, childStyle, child, content) + second.resolveHeight(
            align,
            childStyle,
            child,
            content
        )

        is UiLength.Substraction -> first.resolveHeight(align, childStyle, child, content) - second.resolveHeight(
            align,
            childStyle,
            child,
            content
        )
    }
}

internal fun UiNode.requiresTextLayer(transform: UiMatrix4): Boolean {
    return isInlineFlow() && !transform.isDirectTextTransform()
}

private fun UiMatrix4.isDirectTextTransform(): Boolean {
    val origin = transform(0f, 0f, 0f)
    val xAxis = transform(1f, 0f, 0f)
    val yAxis = transform(0f, 1f, 0f)
    val xDelta = UiVec3(xAxis.x - origin.x, xAxis.y - origin.y, xAxis.z - origin.z)
    val yDelta = UiVec3(yAxis.x - origin.x, yAxis.y - origin.y, yAxis.z - origin.z)
    return abs(xDelta.y) <= DirectTextTransformEpsilon && abs(xDelta.z) <= DirectTextTransformEpsilon &&
            abs(yDelta.x) <= DirectTextTransformEpsilon && abs(yDelta.z) <= DirectTextTransformEpsilon
}

internal fun UiComputedStyle.outerInsets(width: Float, height: Float, reserve: UiScrollbarReserve): ResolvedUiInsets {
    val border = border.width.resolve(width, height)
    val padding = padding.resolve(width, height)
    val verticalScrollbar = scrollbar.resolved(width)
    val horizontalScrollbar = scrollbar.resolved(height)
    return ResolvedUiInsets(
        left = border.left + padding.left,
        top = border.top + padding.top,
        right = border.right + padding.right + if (reserve.vertical) verticalScrollbar.gutter else 0f,
        bottom = border.bottom + padding.bottom + if (reserve.horizontal) horizontalScrollbar.gutter else 0f,
    )
}
