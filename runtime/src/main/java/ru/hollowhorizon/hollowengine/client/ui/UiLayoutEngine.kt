package ru.hollowhorizon.hollowengine.client.ui

import dev.vfyjxf.taffy.geometry.FloatSize
import dev.vfyjxf.taffy.geometry.TaffyPoint
import dev.vfyjxf.taffy.geometry.TaffyRect
import dev.vfyjxf.taffy.geometry.TaffySize
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.AvailableSpace
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.Overflow
import dev.vfyjxf.taffy.style.TaffyDisplay
import dev.vfyjxf.taffy.style.TaffyPosition
import dev.vfyjxf.taffy.style.TaffyStyle
import dev.vfyjxf.taffy.tree.NodeId
import dev.vfyjxf.taffy.tree.TaffyTree
import dev.vfyjxf.taffy.util.MeasureFunc
import kotlin.math.ceil

data class UiRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px <= x + width && py <= y + height
}

data class UiLayoutNode(
    val node: UiNode,
    val rect: UiRect,
    val content: UiRect,
    val clip: UiRect?,
    val worldTransform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val needsFramebuffer: Boolean,
    val scrollOffset: UiScrollOffset = UiScrollOffset.Zero,
    val scrollRange: UiScrollOffset = UiScrollOffset.Zero,
)

data class UiLayoutResult(
    val root: UiNode,
    val nodes: Map<UiNode, UiLayoutNode>,
) {
    operator fun get(node: UiNode): UiLayoutNode = nodes.getValue(node)
}

class UiLayoutEngine {
    fun compute(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val tree = TaffyTree()
        val ids = linkedMapOf<UiNode, NodeId>()
        buildTaffyTree(resolved.root, resolved, tree, ids, UiSize(width.px, height.px))
        tree.computeLayout(
            ids.getValue(resolved.root),
            TaffySize.of(AvailableSpace.definite(width), AvailableSpace.definite(height)),
        )
        val layouts = linkedMapOf<UiNode, UiLayoutNode>()
        collectLayouts(
            resolved.root,
            resolved,
            tree,
            ids,
            UiRect(0f, 0f, width, height),
            true,
            null,
            UiMatrix4.identity(),
            UiMatrix4.identity(),
            scrollState,
            layouts,
        )
        val rangedLayouts = applyScrollRanges(resolved, layouts, scrollState)
        return UiLayoutResult(resolved.root, rangedLayouts)
    }

    private fun buildTaffyTree(
        node: UiNode,
        resolved: ResolvedUiTree,
        tree: TaffyTree,
        ids: MutableMap<UiNode, NodeId>,
        sizeOverride: UiSize? = null,
    ): NodeId {
        val childIds = node.children.map { buildTaffyTree(it, resolved, tree, ids) }
        val style = resolved[node].toTaffyStyle(sizeOverride, node is TextNode)
        val measure = node.measureFunc(resolved[node])
        val id = when {
            childIds.isNotEmpty() -> tree.newWithChildren(style, childIds)
            measure != null -> tree.newLeafWithMeasure(style, measure)
            else -> tree.newLeaf(style)
        }
        ids[node] = id
        return id
    }

    private fun collectLayouts(
        node: UiNode,
        resolved: ResolvedUiTree,
        tree: TaffyTree,
        ids: Map<UiNode, NodeId>,
        parentRect: UiRect,
        useFlowOffset: Boolean,
        parentClip: UiRect?,
        parentTransform: UiMatrix4,
        parentInputTransform: UiMatrix4,
        scrollState: UiScrollState,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val style = resolved[node]
        val layout = tree.getLayout(ids.getValue(node))
        val flowX = if (useFlowOffset) layout.location().x else 0f
        val flowY = if (useFlowOffset) layout.location().y else 0f
        val position = style.position.resolve(parentRect.width, parentRect.height)
        val localX = flowX + position.x
        val localY = flowY + position.y
        val x = parentRect.x + localX
        val y = parentRect.y + localY
        val rect = UiRect(x, y, layout.size().width, layout.size().height)
        val content = UiRect(
            x + layout.border().left + layout.padding().left,
            y + layout.border().top + layout.padding().top,
            layout.contentBoxWidth(),
            layout.contentBoxHeight(),
        )
        val scrollOffset = scrollState.offset(node)
        val clip = if (style.clip || style.input.scrollable) parentClip.intersect(content) else parentClip
        val transformOrigin = UiMatrix4.translation(rect.width * 0.5f, rect.height * 0.5f, 0f)
        val transformOriginInverse = UiMatrix4.translation(-rect.width * 0.5f, -rect.height * 0.5f, 0f)
        val transform = parentTransform *
            UiMatrix4.translation(localX, localY, position.z) *
            transformOrigin *
            style.transform.matrix() *
            transformOriginInverse
        val inputTransform = parentInputTransform *
            UiMatrix4.translation(localX, localY, position.z) *
            transformOrigin *
            style.transform.matrix() *
            transformOriginInverse
        val needsFramebuffer = style.transform.needsFramebuffer ||
            style.filter.requiresLayer ||
            style.backdropFilter.requiresLayer
        layouts[node] = UiLayoutNode(node, rect, content, clip, transform, inputTransform, needsFramebuffer, scrollOffset)

        for (child in node.children) {
            val baseParentRect = if (style.layout == LayoutType.STACK || style.layout == LayoutType.FREE) content else rect
            val nextParentRect = if (style.input.scrollable) {
                baseParentRect.copy(x = baseParentRect.x - scrollOffset.x, y = baseParentRect.y - scrollOffset.y)
            } else {
                baseParentRect
            }
            val nextParentTransform = if (style.input.scrollable) {
                transform * UiMatrix4.translation(-scrollOffset.x, -scrollOffset.y, 0f)
            } else {
                transform
            }
            val nextParentInputTransform = if (style.input.scrollable) {
                inputTransform * UiMatrix4.translation(-scrollOffset.x, -scrollOffset.y, 0f)
            } else {
                inputTransform
            }
            val childUsesFlow = style.layout != LayoutType.STACK && style.layout != LayoutType.FREE
            collectLayouts(
                child,
                resolved,
                tree,
                ids,
                nextParentRect,
                childUsesFlow,
                clip,
                nextParentTransform,
                nextParentInputTransform,
                scrollState,
                layouts,
            )
        }
    }

    private fun applyScrollRanges(
        resolved: ResolvedUiTree,
        layouts: Map<UiNode, UiLayoutNode>,
        scrollState: UiScrollState,
    ): Map<UiNode, UiLayoutNode> {
        val result = layouts.toMutableMap()
        for ((node, layout) in layouts) {
            val style = resolved[node]
            if (!style.input.scrollable) continue
            val childBounds = node.children.mapNotNull { child ->
                layouts[child]?.rect?.let {
                    UiRect(
                        x = it.x + layout.scrollOffset.x,
                        y = it.y + layout.scrollOffset.y,
                        width = it.width,
                        height = it.height,
                    )
                }
            }.union() ?: layout.content
            val range = UiScrollOffset(
                x = maxOf(0f, childBounds.x + childBounds.width - (layout.content.x + layout.content.width)),
                y = maxOf(0f, childBounds.y + childBounds.height - (layout.content.y + layout.content.height)),
            )
            val clamped = scrollState.clamp(node, range)
            result[node] = layout.copy(scrollOffset = clamped, scrollRange = range)
        }
        return result
    }

    private fun ComputedStyle.toTaffyStyle(sizeOverride: UiSize?, textNode: Boolean): TaffyStyle {
        val style = TaffyStyle()
        val resolvedSize = sizeOverride ?: size
        style.display = when (layout) {
            LayoutType.COLUMN, LayoutType.ROW -> TaffyDisplay.FLEX
            LayoutType.GRID -> TaffyDisplay.GRID
            LayoutType.STACK, LayoutType.FREE -> TaffyDisplay.BLOCK
        }
        style.flexDirection = if (layout == LayoutType.ROW) FlexDirection.ROW else FlexDirection.COLUMN
        style.position = TaffyPosition.RELATIVE
        style.size = TaffySize.of(resolvedSize.width.toTaffyDimension(), resolvedSize.height.toTaffyDimension())
        style.minSize = TaffySize.of(minSize.width.toTaffyDimension(), minSize.height.toTaffyDimension())
        style.maxSize = TaffySize.of(maxSize.width.toTaffyDimension(), maxSize.height.toTaffyDimension())
        style.padding = TaffyRect.of(
            padding.left.toLengthPercentage(),
            padding.right.toLengthPercentage(),
            padding.top.toLengthPercentage(),
            padding.bottom.toLengthPercentage(),
        )
        style.margin = TaffyRect.of(
            margin.left.toLengthPercentageAuto(),
            margin.right.toLengthPercentageAuto(),
            margin.top.toLengthPercentageAuto(),
            margin.bottom.toLengthPercentageAuto(),
        )
        style.gap = TaffySize.of(gap.toLengthPercentage(), gap.toLengthPercentage())
        style.flexGrow = grow
        style.alignItems = alignItems.toTaffyAlignItems()
        style.alignSelf = alignSelf.toTaffyAlignItems()
        style.justifySelf = justifySelf.toTaffyAlignItems()
        style.justifyContent = justifyContent.toTaffyAlignContent()
        if (textNode) style.overflow = TaffyPoint(Overflow.HIDDEN, Overflow.VISIBLE)
        return style
    }

    private fun UiNode.measureFunc(style: ComputedStyle): MeasureFunc? {
        if (this !is TextNode) return null
        return MeasureFunc { known: FloatSize, available: TaffySize<AvailableSpace> ->
            val availableWidth = available.width.intoOption().takeIf { !it.isNaN() }
            val width = known.width.takeIf { !it.isNaN() } ?: availableWidth ?: text.template.length * 6f
            val lines = maxOf(1, ceil(text.template.length * 6f / width.coerceAtLeast(1f)).toInt())
            val height = known.height.takeIf { !it.isNaN() } ?: lines * 10f
            FloatSize(width, height)
        }
    }

}

private fun UiRect?.intersect(other: UiRect): UiRect? {
    if (this == null) return other
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
}

private fun List<UiRect>.union(): UiRect? {
    if (isEmpty()) return null
    var left = first().x
    var top = first().y
    var right = first().x + first().width
    var bottom = first().y + first().height
    for (rect in drop(1)) {
        left = minOf(left, rect.x)
        top = minOf(top, rect.y)
        right = maxOf(right, rect.x + rect.width)
        bottom = maxOf(bottom, rect.y + rect.height)
    }
    return UiRect(left, top, right - left, bottom - top)
}

private fun UiAlign.toTaffyAlignItems(): AlignItems = when (this) {
    UiAlign.AUTO -> AlignItems.AUTO
    UiAlign.START -> AlignItems.START
    UiAlign.CENTER -> AlignItems.CENTER
    UiAlign.END -> AlignItems.END
    UiAlign.STRETCH -> AlignItems.STRETCH
    UiAlign.SPACE_BETWEEN,
    UiAlign.SPACE_AROUND,
    UiAlign.SPACE_EVENLY -> AlignItems.AUTO
}

private fun UiAlign.toTaffyAlignContent(): AlignContent = when (this) {
    UiAlign.AUTO -> AlignContent.AUTO
    UiAlign.START -> AlignContent.START
    UiAlign.CENTER -> AlignContent.CENTER
    UiAlign.END -> AlignContent.END
    UiAlign.STRETCH -> AlignContent.STRETCH
    UiAlign.SPACE_BETWEEN -> AlignContent.SPACE_BETWEEN
    UiAlign.SPACE_AROUND -> AlignContent.SPACE_AROUND
    UiAlign.SPACE_EVENLY -> AlignContent.SPACE_EVENLY
}
