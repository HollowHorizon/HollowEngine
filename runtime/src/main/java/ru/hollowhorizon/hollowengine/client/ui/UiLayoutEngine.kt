package ru.hollowhorizon.hollowengine.client.ui

import dev.vfyjxf.taffy.geometry.FloatSize
import dev.vfyjxf.taffy.geometry.TaffyPoint
import dev.vfyjxf.taffy.geometry.TaffyRect
import dev.vfyjxf.taffy.geometry.TaffySize
import dev.vfyjxf.taffy.style.*
import dev.vfyjxf.taffy.tree.NodeId
import dev.vfyjxf.taffy.tree.TaffyTree
import dev.vfyjxf.taffy.util.MeasureFunc
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.utils.literal
import kotlin.math.abs
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
    val scrollArea: UiRect = content,
)

data class UiLayoutResult(
    val root: UiNode,
    val nodes: Map<UiNode, UiLayoutNode>,
) {
    operator fun get(node: UiNode): UiLayoutNode = nodes.getValue(node)
}

private const val DirectTextTransformEpsilon = 0.0001f
private const val EstimatedGlyphWidth = 6f
private const val EstimatedLineHeight = 10
private const val ScrollOverflowEpsilon = 0.01f

private data class UiScrollbarReserve(
    val vertical: Boolean = false,
    val horizontal: Boolean = false,
) {
    val active: Boolean get() = vertical || horizontal

    companion object {
        val None = UiScrollbarReserve()
    }
}

class UiLayoutEngine {
    fun compute(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val initialLayouts = computeLayouts(resolved, width, height, scrollState, emptyMap())
        val scrollbarReserves = detectScrollbarReserves(resolved, initialLayouts)
        val layouts = if (scrollbarReserves.isEmpty()) {
            initialLayouts
        } else {
            computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
        }
        val rangedLayouts = applyScrollRanges(resolved, layouts, scrollState)
        return UiLayoutResult(resolved.root, rangedLayouts)
    }

    private fun computeLayouts(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): Map<UiNode, UiLayoutNode> {
        val tree = TaffyTree()
        val ids = linkedMapOf<UiNode, NodeId>()
        buildTaffyTree(resolved.root, resolved, tree, ids, scrollbarReserves, null, UiSize(width.px, height.px))
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
            null,
            layouts,
        )
        return layouts
    }

    private fun buildTaffyTree(
        node: UiNode,
        resolved: ResolvedUiTree,
        tree: TaffyTree,
        ids: MutableMap<UiNode, NodeId>,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        parentLayout: LayoutType?,
        sizeOverride: UiSize? = null,
    ): NodeId {
        val nodeStyle = resolved[node]
        val childIds = node.children.map { buildTaffyTree(it, resolved, tree, ids, scrollbarReserves, nodeStyle.layout) }
        val style = nodeStyle.toTaffyStyle(
            sizeOverride,
            node is TextNode,
            scrollbarReserves[node] ?: UiScrollbarReserve.None,
            parentLayout,
        )
        val measure = node.measureFunc(resolved)
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
        parentStyle: ComputedStyle?,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val style = resolved[node]
        val layout = tree.getLayout(ids.getValue(node))
        val flowX = if (useFlowOffset) layout.location().x else 0f
        val flowY = if (useFlowOffset) layout.location().y else 0f
        val position = style.position.resolve(parentRect.width, parentRect.height)
        val width = layout.size().width
        val height = layout.size().height
        val margin = style.margin.resolve(parentRect.width, parentRect.height)
        val alignedX = style.effectiveAlignHorizontal(parentStyle)
            ?.let { margin.left + (parentRect.width - width - margin.left - margin.right) * it.originFactor() }
            ?: flowX
        val alignedY = style.effectiveAlignVertical(parentStyle)
            ?.let { margin.top + (parentRect.height - height - margin.top - margin.bottom) * it.originFactor() }
            ?: flowY
        val localX = alignedX + position.x
        val localY = alignedY + position.y
        val x = parentRect.x + localX
        val y = parentRect.y + localY
        val rect = UiRect(x, y, width, height)
        val scrollArea = UiRect(
            x + layout.border().left + layout.padding().left,
            y + layout.border().top + layout.padding().top,
            layout.contentBoxWidth(),
            layout.contentBoxHeight(),
        )
        val content = scrollArea.copy(
            width = (scrollArea.width - layout.scrollbarSize().width).coerceAtLeast(0f),
            height = (scrollArea.height - layout.scrollbarSize().height).coerceAtLeast(0f),
        )
        val scrollOffset = scrollState.offset(node)
        val clip = if (style.clip || style.input.scrollable) parentClip.intersect(content) else parentClip
        val origin = style.transformOrigin(parentStyle, rect.width, rect.height)
        val transformOrigin = UiMatrix4.translation(origin.x, origin.y, 0f)
        val transformOriginInverse = UiMatrix4.translation(-origin.x, -origin.y, 0f)
        val transform = parentTransform * UiMatrix4.translation(
            localX,
            localY,
            position.z
        ) * transformOrigin * style.transform.matrix() * transformOriginInverse
        val inputTransform = parentInputTransform * UiMatrix4.translation(
            localX,
            localY,
            position.z
        ) * transformOrigin * style.transform.matrix() * transformOriginInverse
        val needsFramebuffer =
            style.transform.needsFramebuffer || node.requiresTextLayer(transform) || style.filter.requiresLayer || style.backdropFilter.requiresLayer
        layouts[node] =
            UiLayoutNode(node, rect, content, clip, transform, inputTransform, needsFramebuffer, scrollOffset, scrollArea = scrollArea)

        for (child in node.children) {
            val baseParentRect =
                if (style.layout == LayoutType.STACK || style.layout == LayoutType.FREE) content else rect
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
                style,
                layouts,
            )
        }
    }

    private fun UiNode.requiresTextLayer(transform: UiMatrix4): Boolean {
        return this is TextNode && !transform.isDirectTextTransform()
    }

    private fun UiMatrix4.isDirectTextTransform(): Boolean {
        val origin = transform(0f, 0f, 0f)
        val xAxis = transform(1f, 0f, 0f)
        val yAxis = transform(0f, 1f, 0f)
        val xDelta = UiVec3(xAxis.x - origin.x, xAxis.y - origin.y, xAxis.z - origin.z)
        val yDelta = UiVec3(yAxis.x - origin.x, yAxis.y - origin.y, yAxis.z - origin.z)
        return abs(xDelta.y) <= DirectTextTransformEpsilon && abs(xDelta.z) <= DirectTextTransformEpsilon && abs(yDelta.x) <= DirectTextTransformEpsilon && abs(
            yDelta.z
        ) <= DirectTextTransformEpsilon
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
            val viewport = layout.content
            val hasVerticalScrollbar = layout.scrollArea.width > layout.content.width + ScrollOverflowEpsilon
            val hasHorizontalScrollbar = layout.scrollArea.height > layout.content.height + ScrollOverflowEpsilon
            val range = UiScrollOffset(
                x = if (hasHorizontalScrollbar) {
                    maxOf(0f, childBounds.x + childBounds.width - (viewport.x + viewport.width))
                } else {
                    0f
                },
                y = if (hasVerticalScrollbar) {
                    maxOf(0f, childBounds.y + childBounds.height - (viewport.y + viewport.height))
                } else {
                    0f
                },
            )
            val clamped = scrollState.clamp(node, range)
            val clip = layout.clip?.let { it.intersect(viewport) } ?: viewport
            result[node] = layout.copy(
                content = viewport,
                clip = clip,
                scrollOffset = clamped,
                scrollRange = range,
            )
        }
        return result
    }

    private fun detectScrollbarReserves(
        resolved: ResolvedUiTree,
        layouts: Map<UiNode, UiLayoutNode>,
    ): Map<UiNode, UiScrollbarReserve> {
        val reserves = linkedMapOf<UiNode, UiScrollbarReserve>()
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
            val reserve = UiScrollbarReserve(
                vertical = (childBounds.y + childBounds.height).exceeds(layout.content.y + layout.content.height),
                horizontal = (childBounds.x + childBounds.width).exceeds(layout.content.x + layout.content.width),
            )
            if (reserve.active) reserves[node] = reserve
        }
        return reserves
    }

    private fun ComputedStyle.toTaffyStyle(
        sizeOverride: UiSize?,
        textNode: Boolean,
        scrollbarReserve: UiScrollbarReserve,
        parentLayout: LayoutType?,
    ): TaffyStyle {
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
        aspectRatio?.let { style.aspectRatio = it }
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
        val axisAlignment = resolveChildAlignment()
        style.alignItems = axisAlignment.items.toTaffyAlignItems()
        style.alignSelf = crossAxisSelfAlignment(parentLayout).toTaffyAlignItems()
        style.justifySelf = justifySelf.toTaffyAlignItems()
        style.justifyContent = axisAlignment.content.toTaffyAlignContent()
        if (textNode) style.overflow = TaffyPoint(Overflow.HIDDEN, Overflow.VISIBLE)
        if (scrollbarReserve.active) {
            style.overflow = TaffyPoint(
                if (scrollbarReserve.horizontal) Overflow.SCROLL else Overflow.HIDDEN,
                if (scrollbarReserve.vertical) Overflow.SCROLL else Overflow.HIDDEN,
            )
            style.scrollbarWidth = UiScrollbarGutter
        }
        return style
    }

    private fun UiNode.measureFunc(resolved: ResolvedUiTree): MeasureFunc? {
        if (this !is TextNode) return null
        val textNode = this
        return MeasureFunc { known: FloatSize, available: TaffySize<AvailableSpace> ->
            val style = resolved[textNode]
            val availableWidth = available.width.intoOption().takeIf { !it.isNaN() }
            val font = Minecraft.getInstance()?.font
            val naturalWidth = font?.width(textNode.text.template)?.toFloat() ?: estimateTextWidth(textNode.text.template)
            val width = known.width.takeIf { !it.isNaN() }
                ?: availableWidth?.takeIf { style.textWrap }?.let { minOf(naturalWidth, it) }
                ?: naturalWidth
            val wrapWidth = ceil(width / style.transform.scale.x).toInt().coerceAtLeast(1)
            val lines = if (style.textWrap) {
                font?.split(textNode.text.template.literal, wrapWidth)?.size
                    ?: estimateLineCount(textNode.text.template, wrapWidth)
            } else {
                1
            }
            var lineHeight = font?.lineHeight ?: EstimatedLineHeight
            lineHeight = (lineHeight + style.transform.scale.y).toInt()
            val height = known.height.takeIf { !it.isNaN() } ?: (lines.coerceAtLeast(1) * lineHeight).toFloat()
            FloatSize(width, height)
        }
    }

}

private data class UiAxisAlignment(
    val items: UiAlign,
    val content: UiAlign,
)

private fun ComputedStyle.resolveChildAlignment(): UiAxisAlignment {
    val horizontal = childAlignHorizontal()
    val vertical = childAlignVertical()
    return if (layout == LayoutType.ROW) {
        UiAxisAlignment(
            items = vertical ?: alignItems,
            content = horizontal ?: justifyContent,
        )
    } else {
        UiAxisAlignment(
            items = horizontal ?: alignItems,
            content = vertical ?: justifyContent,
        )
    }
}

private fun ComputedStyle.crossAxisSelfAlignment(parentLayout: LayoutType?): UiAlign {
    val explicit = when (parentLayout) {
        LayoutType.ROW -> alignVertical
        LayoutType.COLUMN, LayoutType.GRID, LayoutType.STACK, LayoutType.FREE -> alignHorizontal
        null -> UiAlign.AUTO
    }.takeUnless { it == UiAlign.AUTO }
    return explicit ?: alignSelf
}

private fun ComputedStyle.transformOrigin(parent: ComputedStyle?, width: Float, height: Float): UiVec3 {
    val horizontal = effectiveAlignHorizontal(parent)
        ?: UiAlign.START
    val vertical = effectiveAlignVertical(parent)
        ?: UiAlign.START
    return UiVec3(width * horizontal.originFactor(), height * vertical.originFactor(), 0f)
}

private fun ComputedStyle.effectiveAlignHorizontal(parent: ComputedStyle?): UiAlign? {
    return alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignHorizontal()
}

private fun ComputedStyle.effectiveAlignVertical(parent: ComputedStyle?): UiAlign? {
    return alignVertical.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignVertical()
}

private fun ComputedStyle.childAlignHorizontal(): UiAlign? {
    return alignItemsHorizontal.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == LayoutType.ROW) justifyContent.takeUnless { it == UiAlign.AUTO }
        else alignItems.takeUnless { it == UiAlign.AUTO }
}

private fun ComputedStyle.childAlignVertical(): UiAlign? {
    return alignItemsVertical.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == LayoutType.ROW) alignItems.takeUnless { it == UiAlign.AUTO }
        else justifyContent.takeUnless { it == UiAlign.AUTO }
}

private fun UiAlign.originFactor(): Float = when (this) {
    UiAlign.START,
    UiAlign.AUTO,
        -> 0f

    UiAlign.CENTER,
    UiAlign.STRETCH,
    UiAlign.SPACE_BETWEEN,
    UiAlign.SPACE_AROUND,
    UiAlign.SPACE_EVENLY,
        -> 0.5f

    UiAlign.END -> 1f
}

private fun estimateTextWidth(text: String): Float = text.length * EstimatedGlyphWidth

private fun estimateLineCount(text: String, width: Int): Int =
    ceil(estimateTextWidth(text) / width.coerceAtLeast(1)).toInt().coerceAtLeast(1)

private fun UiInsets.resolve(parentWidth: Float, parentHeight: Float): ResolvedUiInsets {
    return ResolvedUiInsets(
        left = left.resolve(parentWidth),
        top = top.resolve(parentHeight),
        right = right.resolve(parentWidth),
        bottom = bottom.resolve(parentHeight),
    )
}

private data class ResolvedUiInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private fun UiRect?.intersect(other: UiRect): UiRect {
    if (this == null) return other
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
}

private fun Float.exceeds(limit: Float): Boolean = this - limit > ScrollOverflowEpsilon

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
    UiAlign.SPACE_EVENLY,
        -> AlignItems.AUTO
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
