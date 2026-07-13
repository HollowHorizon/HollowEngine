package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarThumbNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathStrokeLineCap
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathStrokeLineJoin
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*
import ru.hollowhorizon.hollowengine.client.ui.text.Shadow as TextShadow

sealed interface UiRenderCommand {
    val node: UiNode
}

enum class UiRenderPhase {
    BACKGROUND,
    CONTENT,
    OVERLAY,
}

data class BeginLayerCommand(
    override val node: UiNode,
    val rect: UiRect,
    val radius: Float,
    val clipShape: Shape?,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backdropFilter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val opacity: Float,
) : UiRenderCommand

data class EndLayerCommand(
    override val node: UiNode,
) : UiRenderCommand

/**
 * A no-op that forces the renderer to flush the current batch. Inserted between overlapping children of
 * an overlap-capable container so phase batching can't reorder one over the other (see collectNode).
 */
data class FlushBarrierCommand(
    override val node: UiNode,
) : UiRenderCommand

data class DrawBackdropFilterCommand(
    override val node: UiNode,
    val rect: UiRect,
    val radius: Float,
    val filter: UiFilterChain,
    val opacity: Float,
    val transform: UiMatrix4,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawShadowCommand(
    override val node: UiNode,
    val rect: UiRect,
    val radius: Float,
    val shape: Shape?,
    val shadows: List<UiShadow>,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class PushClipCommand(
    override val node: UiNode,
    val rect: UiRect,
    val transform: UiMatrix4,
) : UiRenderCommand

data class PopClipCommand(
    override val node: UiNode,
) : UiRenderCommand

data class DrawBoxCommand(
    override val node: UiNode,
    val rect: UiRect,
    val paint: UiResolvedPaint,
    val border: UiBorder,
    val shadows: List<UiShadow>,
    val opacity: Float,
    val tint: UiColor,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val slice: UiInsets,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val phase: UiRenderPhase = UiRenderPhase.CONTENT,
) : UiRenderCommand

data class DrawShapeCommand(
    override val node: UiNode,
    val rect: UiRect,
    val shape: Shape,
    val fill: UiResolvedPaint,
    val stroke: UiResolvedPaint,
    val strokeWidth: Float,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val phase: UiRenderPhase = UiRenderPhase.CONTENT,
    val strokeLineCap: UiPathStrokeLineCap = UiPathStrokeLineCap.Round,
    val strokeLineJoin: UiPathStrokeLineJoin = UiPathStrokeLineJoin.Round,
    val blurRadius: Float = 0f,
    val spreadRadius: Float = 0f,
) : UiRenderCommand

data class DrawTextCommand(
    override val node: UiNode,
    val rect: UiRect,
    val text: String,
    val color: UiColor,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val wrap: Boolean,
    val overflow: UiTextOverflow,
    val align: UiTextAlign,
    val fontSize: Float,
    val fontFamily: String?,
    val textEffects: List<UiTextEffect>,
    val layout: UiTextLayout,
    val scrollOffset: UiScrollOffset,
    val backfaceVisibility: UiBackfaceVisibility,
    val phase: UiRenderPhase = UiRenderPhase.CONTENT,
) : UiRenderCommand

data class DrawImageCommand(
    override val node: UiNode,
    val rect: UiRect,
    val source: String,
    val opacity: Float,
    val tint: UiColor,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val slice: UiInsets,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val phase: UiRenderPhase = UiRenderPhase.CONTENT,
) : UiRenderCommand

data class DrawItemCommand(
    override val node: UiNode,
    val rect: UiRect,
    val item: String,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawEntityCommand(
    override val node: UiNode,
    val rect: UiRect,
    val entity: String,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

/**
 * Receives draw commands as the node tree is traversed. Rendering is streaming and
 * recursive: commands exist only as per-node parameter objects on their way to the
 * renderer, not as a retained frame-wide list.
 */
fun interface UiRenderSink {
    fun submit(command: UiRenderCommand)
}

operator fun UiRenderSink.plusAssign(command: UiRenderCommand) = submit(command)

class UiCommandRenderer {
    /** Recursively walks the resolved tree, streaming each node's commands into [sink]. */
    fun render(
        resolved: UiNode,
        layout: UiLayoutResult,
        sink: UiRenderSink,
    ) {
        collectNode(
            resolved.root,
            resolved,
            layout,
            sink,
            activeClip = null,
            layoutBoundsMatchVisualBounds = true,
        )
    }

    fun collect(
        resolved: UiNode,
        layout: UiLayoutResult,
    ): List<UiRenderCommand> {
        val commands = mutableListOf<UiRenderCommand>()
        render(resolved, layout) { commands += it }
        return commands
    }

    private fun collectNode(
        node: UiNode,
        resolved: UiNode,
        layout: UiLayoutResult,
        commands: UiRenderSink,
        activeClip: UiRect?,
        layoutBoundsMatchVisualBounds: Boolean,
    ) {
        val style = resolved[node]
        val layoutNode = layout[node]
        val isFramebuffer = layoutNode.needsFramebuffer
        val baseFilter = if (isFramebuffer) UiFilterChain.Empty else style.filter
        val localOpacity = if (isFramebuffer) 1f else style.opacity
        val visibleShadows = style.shadows.filterNot { it.inset }
        val nodeLayoutBoundsMatchVisualBounds =
            layoutBoundsMatchVisualBounds && style.transform == DirectLayoutTransform
        val canCullNode = activeClip != null &&
                nodeLayoutBoundsMatchVisualBounds &&
                !isFramebuffer &&
                visibleShadows.isEmpty() &&
                style.backdropFilter.effects.isEmpty() &&
                style.filter == UiFilterChain.Empty
        val cullNodeCommands = activeClip?.let { canCullNode && !layoutNode.rect.intersectsVisible(it) } == true
        val pushedClip = (style.clip && style.clipShape == null) || style.scrollable

        if (cullNodeCommands && pushedClip) return

        if (!cullNodeCommands && style.backdropFilter.effects.isNotEmpty()) {
            commands += DrawBackdropFilterCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                filter = style.backdropFilter, opacity = style.opacity,
                transform = layoutNode.worldTransform, backfaceVisibility = style.backfaceVisibility
            )
        }

        if (!cullNodeCommands && visibleShadows.isNotEmpty()) {
            commands += DrawShadowCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                shape = style.shape ?: style.clipShape.takeIf { style.clip },
                shadows = visibleShadows, opacity = style.opacity,
                transform = layoutNode.worldTransform, filter = baseFilter,
                backfaceVisibility = style.backfaceVisibility
            )
        }

        if (!cullNodeCommands && isFramebuffer) {
            commands += BeginLayerCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                clipShape = style.clipShape.takeIf { style.clip },
                transform = layoutNode.worldTransform, filter = style.filter,
                backdropFilter = style.backdropFilter, backfaceVisibility = style.backfaceVisibility,
                opacity = style.opacity,
            )
        }

        if (!cullNodeCommands) {
            drawNodeBody(
                node,
                resolved,
                style,
                layoutNode,
                layout,
                commands,
                activeClip,
                localOpacity,
                baseFilter,
                pushedClip,
                nodeLayoutBoundsMatchVisualBounds,
            )
            if (isFramebuffer) commands += EndLayerCommand(node)
        }
    }

    private fun drawNodeBody(
        node: UiNode,
        resolved: UiNode,
        style: UiComputedStyle,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
        commands: UiRenderSink,
        activeClip: UiRect?,
        localOpacity: Float,
        baseFilter: UiFilterChain,
        pushedClip: Boolean,
        layoutBoundsMatchVisualBounds: Boolean,
    ) {
        appendBackgroundCommand(node, style, layoutNode, localOpacity, baseFilter, commands)
        appendCanvasModifiers(
            node,
            layoutNode,
            localOpacity,
            baseFilter,
            UiCanvasDrawLayer.BEHIND,
            UiRenderPhase.BACKGROUND,
            commands,
        )

        if (pushedClip) {
            commands += PushClipCommand(
                node,
                layoutNode.content.localTo(layoutNode.rect),
                layoutNode.worldTransform,
            )
        }

        collectNodeContent(
            node,
            style,
            localOpacity,
            layoutNode,
            layout,
            baseFilter,
            commands,
        )

        val childClip = when {
            !pushedClip -> activeClip
            activeClip == null -> layoutNode.content.takeIf { it.hasVisibleArea() }
            else -> activeClip.visibleIntersection(layoutNode.content)
        }
        if (!pushedClip || childClip != null) {
            val sorted = node.children
                .asSequence()
                .filter { it in layout.nodes }
                .sortedBy { resolved[it].layer }
                .toList()
            val queued = if (sorted.size > 1 && node.childrenCanOverlap()) ArrayList<UiRect>() else null
            sorted.forEach { child ->
                if (queued != null) {
                    val rect = layout[child].rect
                    if (queued.any { it.overlaps(rect) }) {
                        commands += FlushBarrierCommand(node)
                        queued.clear()
                    }
                    queued += rect
                }
                collectNode(
                    child,
                    resolved,
                    layout,
                    commands,
                    activeClip = childClip,
                    layoutBoundsMatchVisualBounds = layoutBoundsMatchVisualBounds,
                )
            }
        }

        appendCanvasModifiers(
            node,
            layoutNode,
            localOpacity,
            baseFilter,
            UiCanvasDrawLayer.OVERLAY,
            UiRenderPhase.OVERLAY,
            commands,
        )

        if (pushedClip) commands += PopClipCommand(node)

        // Framework-synthesized scrollbars render after the content clip is popped so they sit
        // in the gutter, on top of content, clipped only by the container's ancestors.
        layout.scrollbars[node]?.forEach { scrollbar ->
            if (scrollbar in layout.nodes) {
                collectNode(
                    scrollbar,
                    resolved,
                    layout,
                    commands,
                    activeClip = activeClip,
                    layoutBoundsMatchVisualBounds = layoutBoundsMatchVisualBounds,
                )
            }
        }
    }

    private fun appendCanvasModifiers(
        node: UiNode,
        layoutNode: UiLayoutNode,
        opacity: Float,
        filter: UiFilterChain,
        layer: UiCanvasDrawLayer,
        phase: UiRenderPhase,
        commands: UiRenderSink,
    ) {
        var scope: UiCommandCanvasScope? = null
        for (modifier in node.resolvedModifiers) {
            if (modifier !is UiCanvasModifier || modifier.layer != layer) continue
            val activeScope = scope ?: canvasScope(node, layoutNode, opacity, filter, phase, commands).also {
                scope = it
            }
            activeScope.run(modifier.block)
        }
    }

    /**
     * Whether a container positions its children so they can overlap. Flow layouts (rows/columns/inline)
     * never overlap; a box (free/stack) or a custom policy (the overlay/popup host) can.
     */
    private fun UiNode.childrenCanOverlap(): Boolean =
        when ((measurePolicy as? UiBuiltInMeasurePolicy)?.kind) {
            UiBuiltInMeasurePolicyKind.COLUMN,
            UiBuiltInMeasurePolicyKind.ROW,
            UiBuiltInMeasurePolicyKind.LAZY_COLUMN,
            UiBuiltInMeasurePolicyKind.LAZY_ROW,
            UiBuiltInMeasurePolicyKind.INLINE_FLOW,
                -> false

            UiBuiltInMeasurePolicyKind.BOX, null -> true
        }

    private fun collectNodeContent(
        node: UiNode,
        style: UiComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
        filter: UiFilterChain,
        commands: UiRenderSink,
    ) {
        val contentTransform = layoutNode.worldTransform.translated(
            layoutNode.content.x - layoutNode.rect.x,
            layoutNode.content.y - layoutNode.rect.y,
            0f
        )
        val backface = style.backfaceVisibility

        when (node) {
            is SpanNode -> {
                val textLayout = layoutNode.textLayout ?: singleRunSpanLayout(node, style, layoutNode)
                commands += DrawTextCommand(
                    node, layoutNode.content, node.text, style.foreground, opacity, contentTransform,
                    filter, wrap = false, style.textOverflow, style.textAlign, style.fontSize,
                    style.fontFamily, emptyList(),
                    textLayout,
                    layoutNode.scrollOffset, backface
                )
            }

            else -> {
                style.image?.let {
                    commands += DrawImageCommand(
                        node, layoutNode.content, it, opacity, style.tint, contentTransform,
                        false, style.imageFit, style.imageSlice, filter, backface,
                    )
                }
                style.item?.let {
                    commands += DrawItemCommand(
                        node, layoutNode.content, it, opacity, contentTransform, filter, backface,
                    )
                }
                style.entity?.let {
                    commands += DrawEntityCommand(
                        node, layoutNode.content, it, opacity, contentTransform, false, filter, backface,
                    )
                }
            }
        }
    }

    private fun appendBackgroundCommand(
        node: UiNode,
        style: UiComputedStyle,
        layoutNode: UiLayoutNode,
        opacity: Float,
        filter: UiFilterChain,
        commands: UiRenderSink,
    ) {
        layoutNode.inlineDecoration?.let { decoration ->
            appendInlineDecoration(node, style, layoutNode, decoration, opacity, filter, commands)
            return
        }
        val shape = style.shape
        if (shape != null) {
            val fill = style.shapeFill ?: style.background
            val strokePaint =
                style.shapeStroke ?: style.border.takeIf { it.width != UiInsets.Zero }?.let { UiPaint.Color(it.color) }
            val strokeWidth = (style.shapeStrokeWidth ?: style.border.width.left).resolve(layoutNode.rect.width)
            val hasFill = fill != UiPaint.None
            val hasStroke = strokePaint != null && strokePaint != UiPaint.None && strokeWidth > 0f
            if (!hasFill && !hasStroke) return
            val canvas = canvasScope(
                node,
                layoutNode,
                opacity,
                filter,
                UiRenderPhase.BACKGROUND,
                commands,
            )
            if (fill != UiPaint.None) canvas.drawShape(shape, fill)
            if (hasStroke) {
                checkNotNull(strokePaint)
                canvas.drawShape(shape, strokePaint, UiDrawStyle.Stroke(strokeWidth))
            }
            return
        }
        if (style.background == UiPaint.None && style.border.width == UiInsets.Zero) return
        val canvas = canvasScope(
            node,
            layoutNode,
            opacity,
            filter,
            UiRenderPhase.BACKGROUND,
            commands,
        )
        canvas.drawRect(
            paint = style.background,
            radius = style.border.radius,
            border = style.border,
            tint = style.tint,
            fit = style.imageFit,
            slice = style.imageSlice,
        )
    }

    private fun canvasScope(
        node: UiNode,
        layoutNode: UiLayoutNode,
        opacity: Float,
        filter: UiFilterChain,
        phase: UiRenderPhase,
        commands: UiRenderSink,
    ) = UiCommandCanvasScope(
        node = node,
        layoutNode = layoutNode,
        opacity = opacity,
        filter = filter,
        backfaceVisibility = node.resolvedSnapshot.backfaceVisibility,
        phase = phase,
        sink = commands,
    )

    /**
     * An inline group (a span or a nested inline flow) draws its background/border once PER LINE.
     * Each line box already runs continuously from the group's first to its last piece on that line
     * (so internal spaces are painted, never skipped). `box-decoration-break` decides how the border
     * and rounding are sliced: [UiBoxDecorationBreak.CLONE] gives every line the full border+radius;
     * [UiBoxDecorationBreak.SLICE] rounds/borders only the outer line ends for one continuous shape.
     */
    private fun appendInlineDecoration(
        node: UiNode,
        style: UiComputedStyle,
        layoutNode: UiLayoutNode,
        decoration: InlineGroupDecoration,
        opacity: Float,
        filter: UiFilterChain,
        commands: UiRenderSink,
    ) {
        val hasBorder = style.border.width != UiInsets.Zero
        if (style.background == UiPaint.None && !hasBorder) return
        val canvas = canvasScope(
            node = node,
            layoutNode = layoutNode,
            opacity = opacity,
            filter = filter,
            phase = UiRenderPhase.BACKGROUND,
            commands = commands,
        )
        val lines = decoration.lines
        val clone = decoration.decorationBreak == UiBoxDecorationBreak.CLONE
        lines.forEachIndexed { index, box ->
            if (box.width <= 0f || box.height <= 0f) return@forEachIndexed
            val outerEnd = clone || index == 0 || index == lines.lastIndex
            val border = if (hasBorder && outerEnd) {
                style.border
            } else {
                // Middle slice lines keep the background continuous but drop rounding/borders.
                UiBorder(radius = if (outerEnd) style.border.radius else 0f)
            }
            canvas.drawRect(
                rect = UiRect(box.x, box.y, box.width, box.height),
                paint = style.background,
                radius = border.radius,
                border = border,
                tint = style.tint,
                fit = style.imageFit,
                slice = style.imageSlice,
            )
        }
    }

    private fun singleRunSpanLayout(node: SpanNode, style: UiComputedStyle, layoutNode: UiLayoutNode): UiTextLayout {
        val text = node.text
        val width = UiTextLayouter.measureTextWidth(text, style.fontSize, style.fontFamily)
        val height = style.fontSize
        val line = UiTextLine(
            text = text,
            x = 0f,
            y = 0f,
            width = width,
            naturalWidth = width,
            height = height,
            fragments = listOf(UiTextRun(text, UiInlineStyle(effects = style.textEffects), 0f, 0f, width, height)),
        )
        return UiTextLayout(listOf(line), maxOf(width, layoutNode.content.width), height)
    }

}



private fun UiShadow.toTextShadow() = TextShadow(
    offsetX = offset.x,
    offsetY = offset.y,
    blur = blur,
    color = color,
)

sealed interface UiResolvedPaint {
    data object None : UiResolvedPaint
    data class Color(val color: UiColor) : UiResolvedPaint
    data class LinearGradient(val angleDegrees: Float, val stops: List<UiGradientStop>) : UiResolvedPaint
    data class RadialGradient(val gradient: UiRadialGradient) : UiResolvedPaint
    data class Image(val source: String) : UiResolvedPaint
    data class Shader(val name: String) : UiResolvedPaint
}

internal fun UiPaint.resolve(): UiResolvedPaint = when (this) {
    UiPaint.None -> UiResolvedPaint.None
    is UiPaint.Color -> UiResolvedPaint.Color(color)
    is UiPaint.LinearGradient -> UiResolvedPaint.LinearGradient(angleDegrees, stops)
    is UiPaint.RadialGradient -> UiResolvedPaint.RadialGradient(gradient)
    is UiPaint.Image -> UiResolvedPaint.Image(source)
    is UiPaint.Shader -> UiResolvedPaint.Shader(name)
}

data class UiHit(
    val node: UiNode,
    val localX: Float,
    val localY: Float,
)

private sealed interface HitTestTask {
    data class Enter(
        val node: UiNode,
        val ancestorClip: UiRect?,
    ) : HitTestTask

    data class Test(
        val node: UiNode,
        val ancestorClip: UiRect?,
    ) : HitTestTask
}

class UiHitTester {
    fun hitTest(resolved: UiNode, layout: UiLayoutResult, x: Float, y: Float): UiHit? {
        return hitNode(resolved.root, resolved, layout, x, y)
    }

    private fun hitNode(
        node: UiNode,
        resolved: UiNode,
        layout: UiLayoutResult,
        x: Float,
        y: Float,
    ): UiHit? {
        val stack = ArrayDeque<HitTestTask>()
        stack.add(HitTestTask.Enter(node, null))
        while (stack.isNotEmpty()) {
            when (val task = stack.removeLast()) {
                is HitTestTask.Enter -> {
                    val current = task.node
                    val layoutNode = layout[current]
                    val effectiveClip = task.ancestorClip.intersect(layoutNode.clip)
                    val children = current.children.toList()
                        .filter { it in layout.nodes }
                        .sortedBy { resolved[it].layer }

                    stack.add(HitTestTask.Test(current, task.ancestorClip))
                    for (child in children) {
                        stack.add(HitTestTask.Enter(child, effectiveClip))
                    }
                    layout.scrollbars[current]?.forEach { scrollbar ->
                        if (scrollbar in layout.nodes) stack.add(HitTestTask.Enter(scrollbar, task.ancestorClip))
                    }
                }

                is HitTestTask.Test -> {
                    val current = task.node
                    val style = resolved[current]
                    if (current.hasEffectiveState(UiState.DISABLED)) continue
                    if (!style.hoverable &&
                        !style.clickable &&
                        !style.focusable &&
                        !style.draggable &&
                        !style.scrollable
                    ) {
                        continue
                    }
                    task.ancestorClip?.let { clip ->
                        if (!clip.contains(x, y)) continue
                    }
                    val layoutNode = layout[current]
                    if (!layoutNode.inputQuadContains(x, y)) continue
                    val inverse = layoutNode.inputTransform.inverse() ?: continue
                    val local = inverse.transform(x, y, 0f)
                    val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
                    if (!rect.contains(local.x, local.y)) continue
                    return UiHit(current, local.x, local.y)
                }
            }
        }
        return null
    }

    fun hitsVisible(resolved: UiNode, layout: UiLayoutResult, x: Float, y: Float): Boolean {
        return visibleNode(resolved.root, resolved, layout, x, y)
    }

    private fun visibleNode(node: UiNode, resolved: UiNode, layout: UiLayoutResult, x: Float, y: Float): Boolean {
        val stack = ArrayDeque<HitTestTask>()
        stack.add(HitTestTask.Enter(node, null))
        while (stack.isNotEmpty()) {
            when (val task = stack.removeLast()) {
                is HitTestTask.Enter -> {
                    val current = task.node
                    val layoutNode = layout[current]
                    val effectiveClip = task.ancestorClip.intersect(layoutNode.clip)
                    val children = current.children.toList().filter { it in layout.nodes }.sortedBy { resolved[it].layer }
                    stack.add(HitTestTask.Test(current, task.ancestorClip))
                    for (child in children) {
                        stack.add(HitTestTask.Enter(child, effectiveClip))
                    }
                    layout.scrollbars[current]?.forEach {
                        if (it in layout.nodes) stack.add(
                            HitTestTask.Enter(
                                it,
                                task.ancestorClip
                            )
                        )
                    }
                }

                is HitTestTask.Test -> {
                    val current = task.node
                    val style = resolved[current]
                    if (style.inputTransparent || !current.paintsGeometry(style)) continue
                    task.ancestorClip?.let { if (!it.contains(x, y)) continue }
                    val layoutNode = layout[current]
                    if (!layoutNode.inputQuadContains(x, y)) continue
                    val inverse = layoutNode.inputTransform.inverse() ?: continue
                    val local = inverse.transform(x, y, 0f)
                    if (!UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height).contains(
                            local.x,
                            local.y
                        )
                    ) continue
                    return true
                }
            }
        }
        return false
    }

    private fun UiNode.paintsGeometry(style: UiComputedStyle): Boolean = when (this) {
        is SpanNode, is ScrollbarNode, is ScrollbarThumbNode,
            -> true

        else -> style.image != null || style.item != null || style.entity != null ||
                style.background != UiPaint.None || style.border.width != UiInsets.Zero || style.shape != null
    }

    private fun UiLayoutNode.inputQuadContains(x: Float, y: Float): Boolean {
        val corners = arrayOf(
            inputTransform.transform(0f, 0f),
            inputTransform.transform(0f, rect.height),
            inputTransform.transform(rect.width, rect.height),
            inputTransform.transform(rect.width, 0f),
        )
        return pointInConvexPolygon(x, y, corners)
    }

    private fun pointInConvexPolygon(x: Float, y: Float, corners: Array<UiVec3>): Boolean {
        if (corners.size < 3) return false
        var sign = 0f
        for (index in corners.indices) {
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            val cross = (next.x - current.x) * (y - current.y) - (next.y - current.y) * (x - current.x)
            if (cross == 0f) continue
            if (sign == 0f) {
                sign = cross
            } else if (sign * cross < 0f) {
                return false
            }
        }
        return true
    }
}

private val DirectLayoutTransform = UiTransform()

private fun UiRect.hasVisibleArea(): Boolean {
    return width > 0f && height > 0f
}

private fun UiRect.visibleIntersection(other: UiRect): UiRect? {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return null
    return UiRect(left, top, right - left, bottom - top)
}

private fun UiRect.intersectsVisible(other: UiRect): Boolean {
    return visibleIntersection(other) != null
}

private fun UiRect.localTo(parent: UiRect): UiRect {
    return copy(x = x - parent.x, y = y - parent.y)
}
