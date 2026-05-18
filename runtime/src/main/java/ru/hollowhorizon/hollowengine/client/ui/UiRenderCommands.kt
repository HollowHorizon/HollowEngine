package ru.hollowhorizon.hollowengine.client.ui

sealed interface UiRenderCommand {
    val node: UiNode
}

data class BeginLayerCommand(
    override val node: UiNode,
    val rect: UiRect,
    val radius: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backdropFilter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class EndLayerCommand(
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
    val shadows: List<UiShadow>,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class PushClipCommand(
    override val node: UiNode,
    val rect: UiRect,
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
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
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
    val align: UiTextAlign,
    val fontSize: Float,
    val layout: UiTextLayout,
    val scrollOffset: UiScrollOffset,
    val hoveredLink: String?,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawImageCommand(
    override val node: UiNode,
    val rect: UiRect,
    val source: String,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
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

data class DrawCanvasCommand(
    override val node: UiNode,
    val rect: UiRect,
    val renderer: String?,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawScrollbarCommand(
    override val node: UiNode,
    val track: UiRect,
    val thumb: UiRect,
    val orientation: ScrollbarOrientation,
    val opacity: Float,
) : UiRenderCommand

enum class ScrollbarOrientation {
    VERTICAL, HORIZONTAL
}

internal const val UiScrollbarThickness = 7f
internal const val UiScrollbarMargin = 3f
internal const val UiScrollbarGutter = UiScrollbarThickness + UiScrollbarMargin * 2f

class UiCommandRenderer {
    fun collect(
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        bindings: UiBindingContext = UiBindingContext(),
    ): List<UiRenderCommand> {
        val commands = mutableListOf<UiRenderCommand>()
        collectNode(resolved.root, resolved, layout, bindings, commands)
        return commands
    }

    private fun collectNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        bindings: UiBindingContext,
        commands: MutableList<UiRenderCommand>,
    ) {
        val style = resolved[node]
        val layoutNode = layout[node]
        if (style.backdropFilter.effects.isNotEmpty()) {
            commands += DrawBackdropFilterCommand(
                node = node,
                rect = layoutNode.rect,
                radius = style.border.radius,
                filter = style.backdropFilter,
                opacity = style.opacity,
                transform = layoutNode.worldTransform,
                backfaceVisibility = style.backfaceVisibility,
            )
        }
        val visibleShadows = style.shadows.filterNot { it.inset }
        if (visibleShadows.isNotEmpty()) {
            commands += DrawShadowCommand(
                node = node,
                rect = layoutNode.rect,
                radius = style.border.radius,
                shadows = visibleShadows,
                opacity = style.opacity,
                transform = layoutNode.worldTransform,
                filter = if (layoutNode.needsFramebuffer) UiFilterChain.Empty else style.filter,
                backfaceVisibility = style.backfaceVisibility,
            )
        }
        if (layoutNode.needsFramebuffer) {
            commands += BeginLayerCommand(
                node = node,
                rect = layoutNode.rect,
                radius = style.border.radius,
                transform = layoutNode.worldTransform,
                filter = style.filter,
                backdropFilter = style.backdropFilter,
                backfaceVisibility = style.backfaceVisibility,
            )
        }
        if (style.background != UiPaint.None || style.border.width != UiInsets.Zero) {
            val commandFilter = if (layoutNode.needsFramebuffer) UiFilterChain.Empty else style.filter
            commands += DrawBoxCommand(
                node = node,
                rect = layoutNode.rect,
                paint = style.background.resolve(bindings),
                border = style.border,
                shadows = emptyList(),
                opacity = style.opacity,
                transform = layoutNode.worldTransform,
                renderToFramebuffer = false,
                fit = style.imageFit,
                filter = commandFilter,
                backfaceVisibility = style.backfaceVisibility,
            )
        }
        val pushedClip = style.clip || style.input.scrollable
        if (pushedClip) commands += PushClipCommand(node, layoutNode.content)
        val contentFilter = if (layoutNode.needsFramebuffer) UiFilterChain.Empty else style.filter
        val contentTransform = layoutNode.worldTransform * UiMatrix4.translation(
            layoutNode.content.x - layoutNode.rect.x,
            layoutNode.content.y - layoutNode.rect.y,
            0f,
        )
        when (node) {
            is TextNode -> commands += DrawTextCommand(
                node,
                layoutNode.content,
                node.text.resolve(bindings),
                style.foreground,
                style.opacity,
                contentTransform,
                contentFilter,
                style.textWrap,
                style.textAlign,
                style.fontSize,
                UiTextLayouter.layout(
                    node.text.resolve(bindings),
                    layoutNode.content.width,
                    if (style.input.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height,
                    style.textWrap,
                    style.textAlign,
                    style.fontSize,
                ),
                layoutNode.scrollOffset,
                node.hoveredLink,
                style.backfaceVisibility,
            )

            is ImageNode -> commands += DrawImageCommand(
                node,
                layoutNode.content,
                node.source.resolve(bindings),
                style.opacity,
                contentTransform,
                false,
                style.imageFit,
                contentFilter,
                style.backfaceVisibility,
            )

            is ItemNode -> commands += DrawItemCommand(
                node,
                layoutNode.content,
                node.item.resolve(bindings),
                style.opacity,
                contentTransform,
                contentFilter,
                style.backfaceVisibility,
            )

            is EntityNode -> commands += DrawEntityCommand(
                node,
                layoutNode.content,
                node.entity.resolve(bindings),
                style.opacity,
                contentTransform,
                false,
                contentFilter,
                style.backfaceVisibility,
            )

            is CanvasNode -> commands += DrawCanvasCommand(
                node,
                layoutNode.content,
                node.renderer,
                style.opacity,
                contentTransform,
                false,
                contentFilter,
                style.backfaceVisibility,
            )
        }
        node.children.sortedBy { resolved[it].layer }.forEach { collectNode(it, resolved, layout, bindings, commands) }
        if (pushedClip) commands += PopClipCommand(node)
        if (style.input.scrollable) {
            appendScrollbars(node, layoutNode, style, commands)
        }
        if (layoutNode.needsFramebuffer) {
            commands += EndLayerCommand(node)
        }
    }

    private fun appendScrollbars(
        node: UiNode,
        layoutNode: UiLayoutNode,
        style: ComputedStyle,
        commands: MutableList<UiRenderCommand>,
    ) {
        val minimumThumb = 18f
        val hasVerticalScrollbar = layoutNode.scrollRange.y > 0f && layoutNode.scrollArea.height > UiScrollbarGutter
        val hasHorizontalScrollbar = layoutNode.scrollRange.x > 0f && layoutNode.scrollArea.width > UiScrollbarGutter
        if (hasVerticalScrollbar) {
            val horizontalReserve = if (hasHorizontalScrollbar) UiScrollbarGutter else 0f
            val trackHeight = layoutNode.scrollArea.height - UiScrollbarMargin * 2f - horizontalReserve
            if (trackHeight > 0f) {
                val track = UiRect(
                    x = layoutNode.scrollArea.x + layoutNode.scrollArea.width - UiScrollbarThickness - UiScrollbarMargin,
                    y = layoutNode.scrollArea.y + UiScrollbarMargin,
                    width = UiScrollbarThickness,
                    height = trackHeight,
                )
                val contentHeight = layoutNode.content.height + layoutNode.scrollRange.y
                val thumbHeight = maxOf(minimumThumb, track.height * layoutNode.content.height / contentHeight)
                val thumbY = track.y + (track.height - thumbHeight) * (layoutNode.scrollOffset.y / layoutNode.scrollRange.y)
                commands += DrawScrollbarCommand(
                    node,
                    track,
                    track.copy(y = thumbY, height = thumbHeight),
                    ScrollbarOrientation.VERTICAL,
                    style.opacity
                )
            }
        }
        if (hasHorizontalScrollbar) {
            val verticalReserve = if (hasVerticalScrollbar) UiScrollbarGutter else 0f
            val trackWidth = layoutNode.scrollArea.width - UiScrollbarMargin * 2f - verticalReserve
            if (trackWidth > 0f) {
                val track = UiRect(
                    x = layoutNode.scrollArea.x + UiScrollbarMargin,
                    y = layoutNode.scrollArea.y + layoutNode.scrollArea.height - UiScrollbarThickness - UiScrollbarMargin,
                    width = trackWidth,
                    height = UiScrollbarThickness,
                )
                val contentWidth = layoutNode.content.width + layoutNode.scrollRange.x
                val thumbWidth = maxOf(minimumThumb, track.width * layoutNode.content.width / contentWidth)
                val thumbX = track.x + (track.width - thumbWidth) * (layoutNode.scrollOffset.x / layoutNode.scrollRange.x)
                commands += DrawScrollbarCommand(
                    node,
                    track,
                    track.copy(x = thumbX, width = thumbWidth),
                    ScrollbarOrientation.HORIZONTAL,
                    style.opacity
                )
            }
        }
    }
}

sealed interface UiResolvedPaint {
    data object None : UiResolvedPaint
    data class Color(val color: UiColor) : UiResolvedPaint
    data class LinearGradient(val angleDegrees: Float, val stops: List<UiGradientStop>) : UiResolvedPaint
    data class Image(val source: String) : UiResolvedPaint
    data class Shader(val name: String) : UiResolvedPaint
}

private fun UiPaint.resolve(bindings: UiBindingContext): UiResolvedPaint = when (this) {
    UiPaint.None -> UiResolvedPaint.None
    is UiPaint.Color -> UiResolvedPaint.Color(color)
    is UiPaint.LinearGradient -> UiResolvedPaint.LinearGradient(angleDegrees, stops)
    is UiPaint.Image -> UiResolvedPaint.Image(source.resolve(bindings))
    is UiPaint.Shader -> UiResolvedPaint.Shader(name.resolve(bindings))
}

data class UiHit(
    val node: UiNode,
    val localX: Float,
    val localY: Float,
    val link: String? = null,
)

class UiHitTester {
    fun hitTest(resolved: ResolvedUiTree, layout: UiLayoutResult, x: Float, y: Float): UiHit? {
        return hitNode(resolved.root, resolved, layout, x, y)
    }

    private fun hitNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        x: Float,
        y: Float,
    ): UiHit? {
        val children = node.children.sortedWith(compareBy<UiNode> { resolved[it].layer }.thenBy { layout[it].rect.y })
        for (child in children.asReversed()) {
            hitNode(child, resolved, layout, x, y)?.let { return it }
        }
        val style = resolved[node]
        if (UiState.DISABLED in node.states) return null
        if (!style.input.hoverable && !style.input.clickable && !style.input.focusable && !style.input.draggable && !style.input.scrollable) {
            return null
        }
        val layoutNode = layout[node]
        if (!layoutNode.inputQuadContains(x, y)) return null
        val inverse = layoutNode.inputTransform.inverse() ?: return null
        val local = inverse.transform(x, y, 0f)
        val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
        if (!rect.contains(local.x, local.y)) return null
        layoutNode.clip?.let { clip ->
            if (!clip.contains(x, y)) return null
        }
        return UiHit(node, local.x, local.y)
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
