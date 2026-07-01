package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.UiLayout
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.ComputedStyle
import ru.hollowhorizon.hollowengine.client.ui.style.ResolvedUiTree

internal interface ChildLayoutPolicy {
    fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope)

    fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize
}

internal data class ChildPlacementScope(
    val node: UiNode,
    val resolved: ResolvedUiTree,
    val style: ComputedStyle,
    val layout: UiLayout,
    val content: UiRect,
    val parentRect: UiRect,
    val transform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val clip: UiRect?,
    val insideFramebuffer: Boolean,
    val scrollState: UiScrollState,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
)

internal data class ChildIntrinsicScope(
    val children: List<MeasuredChild>,
    val availableWidth: Float,
    val availableHeight: Float,
    val knownContentWidth: Float?,
    val knownContentHeight: Float?,
    val gap: Float,
    val resolved: ResolvedUiTree,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
)

internal fun UiLayout.policy(): ChildLayoutPolicy = when (this) {
    UiLayout.Row -> RowPolicy
    UiLayout.Column -> ColumnPolicy
    UiLayout.LazyColumn -> LazyColumnPolicy
    UiLayout.LazyRow -> LazyRowPolicy
    is UiLayout.Box -> BoxPolicy
    is UiLayout.Custom -> CustomPolicy
}

private object RowPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeRowChildren(
            scope.node,
            scope.resolved,
            scope.style,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
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
        pipeline.placeColumnChildren(
            scope.node,
            scope.resolved,
            scope.style,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
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

private object LazyColumnPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeLazyColumnChildren(
            scope.node,
            scope.resolved,
            scope.style,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.maxOfOuterWidth(),
            scope.children.sumOfOuterHeight() + scope.gap * (scope.children.size - 1).coerceAtLeast(0),
        )
    }
}

private object LazyRowPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeLazyRowChildren(
            scope.node,
            scope.resolved,
            scope.style,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.sumOfOuterWidth() + scope.gap * (scope.children.size - 1).coerceAtLeast(0),
            scope.children.maxOfOuterHeight(),
        )
    }
}

private object BoxPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeFreeChildren(
            scope.node,
            scope.resolved,
            scope.style,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.maxOfPositionedOuterWidth(scope.availableWidth, scope.availableHeight),
            scope.children.maxOfPositionedOuterHeight(scope.availableWidth, scope.availableHeight),
        )
    }
}

private object CustomPolicy : ChildLayoutPolicy {
    override fun place(pipeline: UiLayoutPipeline, scope: ChildPlacementScope) {
        pipeline.placeCustomChildren(
            scope.node,
            scope.resolved,
            scope.layout as UiLayout.Custom,
            scope.content,
            scope.parentRect,
            scope.transform,
            scope.inputTransform,
            scope.clip,
            scope.insideFramebuffer,
            scope.scrollState,
            scope.scrollbarReserves,
            scope.layouts,
        )
    }

    override fun intrinsic(pipeline: UiLayoutPipeline, scope: ChildIntrinsicScope): LayoutSize {
        return LayoutSize(
            scope.children.maxOfPositionedOuterWidth(scope.availableWidth, scope.availableHeight),
            scope.children.maxOfPositionedOuterHeight(scope.availableWidth, scope.availableHeight),
        )
    }
}
