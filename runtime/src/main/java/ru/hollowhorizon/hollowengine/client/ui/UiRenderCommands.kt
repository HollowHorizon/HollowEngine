package ru.hollowhorizon.hollowengine.client.ui

sealed interface UiRenderCommand {
    val node: UiNode
}

data class BeginLayerCommand(
    override val node: UiNode,
    val rect: UiRect,
    val transform: UiMatrix4,
) : UiRenderCommand

data class EndLayerCommand(
    override val node: UiNode,
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
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
) : UiRenderCommand

data class DrawTextCommand(
    override val node: UiNode,
    val rect: UiRect,
    val text: String,
    val color: UiColor,
    val opacity: Float,
    val transform: UiMatrix4,
) : UiRenderCommand

data class DrawImageCommand(
    override val node: UiNode,
    val rect: UiRect,
    val source: String,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
) : UiRenderCommand

data class DrawItemCommand(
    override val node: UiNode,
    val rect: UiRect,
    val item: String,
    val opacity: Float,
    val transform: UiMatrix4,
) : UiRenderCommand

data class DrawEntityCommand(
    override val node: UiNode,
    val rect: UiRect,
    val entity: String,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
) : UiRenderCommand

data class DrawCanvasCommand(
    override val node: UiNode,
    val rect: UiRect,
    val renderer: String?,
    val opacity: Float,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
) : UiRenderCommand

data class DrawScrollbarCommand(
    override val node: UiNode,
    val track: UiRect,
    val thumb: UiRect,
    val orientation: ScrollbarOrientation,
    val opacity: Float,
) : UiRenderCommand

enum class ScrollbarOrientation {
    VERTICAL,
    HORIZONTAL
}

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
        if (layoutNode.needsFramebuffer) {
            commands += BeginLayerCommand(node, layoutNode.rect, layoutNode.worldTransform)
        }
        val pushedClip = style.clip || style.input.scrollable
        if (pushedClip) commands += PushClipCommand(node, layoutNode.content)
        if (style.background != UiPaint.None || style.border.width != UiInsets.Zero) {
            commands += DrawBoxCommand(
                node = node,
                rect = layoutNode.rect,
                paint = style.background.resolve(bindings),
                border = style.border,
                opacity = style.opacity,
                transform = layoutNode.worldTransform,
                renderToFramebuffer = false,
            )
        }
        when (node) {
            is TextNode -> commands += DrawTextCommand(
                node,
                layoutNode.content,
                node.text.resolve(bindings),
                style.foreground,
                style.opacity,
                layoutNode.worldTransform,
            )
            is ImageNode -> commands += DrawImageCommand(
                node,
                layoutNode.content,
                node.source.resolve(bindings),
                style.opacity,
                layoutNode.worldTransform,
                false,
                style.imageFit,
            )
            is ItemNode -> commands += DrawItemCommand(
                node,
                layoutNode.content,
                node.item.resolve(bindings),
                style.opacity,
                layoutNode.worldTransform,
            )
            is EntityNode -> commands += DrawEntityCommand(
                node,
                layoutNode.content,
                node.entity.resolve(bindings),
                style.opacity,
                layoutNode.worldTransform,
                false,
            )
            is CanvasNode -> commands += DrawCanvasCommand(
                node,
                layoutNode.content,
                node.renderer,
                style.opacity,
                layoutNode.worldTransform,
                false,
            )
        }
        node.children
            .sortedBy { resolved[it].layer }
            .forEach { collectNode(it, resolved, layout, bindings, commands) }
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
        val thickness = 7f
        val minimumThumb = 18f
        if (layoutNode.scrollRange.y > 0f && layoutNode.content.height > thickness) {
            val track = UiRect(
                x = layoutNode.content.x + layoutNode.content.width - thickness,
                y = layoutNode.content.y,
                width = thickness,
                height = layoutNode.content.height,
            )
            val contentHeight = layoutNode.content.height + layoutNode.scrollRange.y
            val thumbHeight = maxOf(minimumThumb, track.height * layoutNode.content.height / contentHeight)
            val thumbY = track.y + (track.height - thumbHeight) * (layoutNode.scrollOffset.y / layoutNode.scrollRange.y)
            commands += DrawScrollbarCommand(node, track, track.copy(y = thumbY, height = thumbHeight), ScrollbarOrientation.VERTICAL, style.opacity)
        }
        if (layoutNode.scrollRange.x > 0f && layoutNode.content.width > thickness) {
            val track = UiRect(
                x = layoutNode.content.x,
                y = layoutNode.content.y + layoutNode.content.height - thickness,
                width = layoutNode.content.width,
                height = thickness,
            )
            val contentWidth = layoutNode.content.width + layoutNode.scrollRange.x
            val thumbWidth = maxOf(minimumThumb, track.width * layoutNode.content.width / contentWidth)
            val thumbX = track.x + (track.width - thumbWidth) * (layoutNode.scrollOffset.x / layoutNode.scrollRange.x)
            commands += DrawScrollbarCommand(node, track, track.copy(x = thumbX, width = thumbWidth), ScrollbarOrientation.HORIZONTAL, style.opacity)
        }
    }
}

sealed interface UiResolvedPaint {
    data object None : UiResolvedPaint
    data class Color(val color: UiColor) : UiResolvedPaint
    data class Image(val source: String) : UiResolvedPaint
    data class Shader(val name: String) : UiResolvedPaint
}

private fun UiPaint.resolve(bindings: UiBindingContext): UiResolvedPaint = when (this) {
    UiPaint.None -> UiResolvedPaint.None
    is UiPaint.Color -> UiResolvedPaint.Color(color)
    is UiPaint.Image -> UiResolvedPaint.Image(source.resolve(bindings))
    is UiPaint.Shader -> UiResolvedPaint.Shader(name.resolve(bindings))
}

data class UiHit(
    val node: UiNode,
    val localX: Float,
    val localY: Float,
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
        val inverse = layoutNode.inputTransform.inverse() ?: return null
        val local = inverse.transform(x, y, 0f)
        val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
        if (!rect.contains(local.x, local.y)) return null
        layoutNode.clip?.let { clip ->
            if (!clip.contains(x, y)) return null
        }
        return UiHit(node, local.x, local.y)
    }
}
