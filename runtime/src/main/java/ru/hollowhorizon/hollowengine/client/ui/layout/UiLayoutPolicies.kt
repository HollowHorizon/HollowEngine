package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle
import ru.hollowhorizon.hollowengine.client.ui.style.lineSpacing
import ru.hollowhorizon.hollowengine.client.ui.style.textWrap

internal interface ChildLayoutPolicy {
    fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope)

    fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize
}

internal data class ChildPlacementScope(
    val node: UiNode,
    val resolved: UiNode,
    val style: UiComputedStyle,
    val measurePolicy: UiMeasurePolicy,
    val content: UiRect,
    val parentRect: UiRect,
    val transform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val clip: UiRect?,
    val insideFramebuffer: Boolean,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
)

internal data class ChildIntrinsicScope(
    val node: UiNode,
    val children: List<MeasuredChild>,
    val availableWidth: Float,
    val availableHeight: Float,
    val knownContentWidth: Float?,
    val knownContentHeight: Float?,
    val gap: Float,
    val resolved: UiNode,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
)

internal fun UiMeasurePolicy.policy(): ChildLayoutPolicy = when ((this as? UiBuiltInMeasurePolicy)?.kind) {
    UiBuiltInMeasurePolicyKind.ROW -> RowPolicy
    UiBuiltInMeasurePolicyKind.COLUMN -> ColumnPolicy
    UiBuiltInMeasurePolicyKind.BOX -> BoxPolicy
    UiBuiltInMeasurePolicyKind.INLINE_FLOW -> InlineFlowPolicy
    null -> CustomPolicy
}

internal fun UiMeasurePolicy.flowAxis(): FlowAxis? {
    return when ((this as? UiBuiltInMeasurePolicy)?.kind) {
        UiBuiltInMeasurePolicyKind.ROW -> FlowAxis.Horizontal
        UiBuiltInMeasurePolicyKind.COLUMN -> FlowAxis.Vertical

        UiBuiltInMeasurePolicyKind.BOX,
        UiBuiltInMeasurePolicyKind.INLINE_FLOW,
        null,
            -> null
    }
}

private object RowPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeLinearChildren(FlowAxis.Horizontal, scope)
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        val rowChildren = scope.knownContentWidth
            ?.let {
                pipeline.growRowChildren(
                    scope.children,
                    it,
                    scope.gap,
                    scope.resolved,
                    scope.scrollbarReserves,
                )
            }
            ?: scope.children
        return LayoutSize(
            rowChildren.sumOfOuterWidth() + scope.gap * (rowChildren.size - 1).coerceAtLeast(0),
            rowChildren.maxOfOuterHeight(),
        )
    }
}

private object ColumnPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeLinearChildren(FlowAxis.Vertical, scope)
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        val columnChildren = scope.knownContentHeight
            ?.let {
                pipeline.growColumnChildren(
                    scope.children,
                    it,
                    scope.gap,
                    scope.resolved,
                    scope.scrollbarReserves,
                )
            }
            ?: scope.children
        return LayoutSize(
            columnChildren.maxOfOuterWidth(),
            columnChildren.sumOfOuterHeight() + scope.gap * (columnChildren.size - 1).coerceAtLeast(0),
        )
    }
}

private object BoxPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeFreeChildren(scope)
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.maxOfPositionedOuterWidth(scope.availableWidth, scope.availableHeight),
            scope.children.maxOfPositionedOuterHeight(scope.availableWidth, scope.availableHeight),
        )
    }
}

private object InlineFlowPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeInlineFlowChildren(scope)
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        val containerStyle = scope.resolved[scope.node]
        val flow = pipeline.computeInlineFlow(
            scope.node,
            scope.resolved,
            scope.knownContentWidth ?: scope.availableWidth,
            scope.availableHeight,
            UiAlign.START,
            containerStyle.lineSpacing,
            wrap = containerStyle.textWrap,
            scope.scrollbarReserves,
        )
        return LayoutSize(flow.width, flow.height)
    }
}

private object CustomPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeCustomChildren(scope, scope.measurePolicy)
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.maxOfPositionedOuterWidth(scope.availableWidth, scope.availableHeight),
            scope.children.maxOfPositionedOuterHeight(scope.availableWidth, scope.availableHeight),
        )
    }
}
