package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.effects.Shadow as TextShadow

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
    val opacity: Float,
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
    val tint: UiColor,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val slice: UiInsets,
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
    val fontFamily: String?,
    val textEffects: List<UiTextEffect>,
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
    val tint: UiColor,
    val transform: UiMatrix4,
    val renderToFramebuffer: Boolean,
    val fit: UiImageFit,
    val slice: UiInsets,
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

data class DrawSliderCommand(
    override val node: SliderNode,
    val rect: UiRect,
    val value: Float,
    val fraction: Float,
    val trackThickness: Float,
    val trackPaint: UiResolvedPaint,
    val activeTrackPaint: UiResolvedPaint,
    val thumbPaint: UiResolvedPaint,
    val thumbBorder: UiBorder,
    val thumbWidth: Float,
    val thumbHeight: Float,
    val radius: Float,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawCheckboxCommand(
    override val node: CheckboxNode,
    val rect: UiRect,
    val checked: Boolean,
    val variant: UiCheckboxVariant,
    val activePaint: UiResolvedPaint,
    val markPaint: UiResolvedPaint,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawTextFieldChromeCommand(
    override val node: TextFieldNode,
    val rect: UiRect,
    val layout: UiTextLayout,
    val scrollOffset: UiScrollOffset,
    val caretIndex: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val caretColor: UiColor,
    val selectionColor: UiColor,
    val lineNumberColor: UiColor,
    val inlayHintColor: UiColor,
    val showCaret: Boolean,
    val showLineNumbers: Boolean,
    val showInlayHints: Boolean,
    val placeholder: String,
    val opacity: Float,
    val fontSize: Float,
    val fontFamily: String?,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

data class DrawScrollbarCommand(
    override val node: UiNode,
    val track: UiRect,
    val thumb: UiRect,
    val orientation: ScrollbarOrientation,
    val trackPaint: UiResolvedPaint,
    val trackBorder: UiBorder,
    val trackFit: UiImageFit,
    val trackSlice: UiInsets,
    val thumbPaint: UiResolvedPaint,
    val thumbBorder: UiBorder,
    val thumbFit: UiImageFit,
    val thumbSlice: UiInsets,
    val opacity: Float,
    val transform: UiMatrix4,
) : UiRenderCommand

enum class ScrollbarOrientation {
    VERTICAL, HORIZONTAL
}

class UiCommandRenderer {
    fun collect(
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
        typingState: UiTypingState = UiTypingState(),
    ): List<UiRenderCommand> {
        val commands = mutableListOf<UiRenderCommand>()
        collectNode(resolved.root, resolved, layout, bindings, nowMillis, typingState, commands)
        resolved.root.popupDescendants()
            .sortedBy { resolved[it].layer }
            .forEach { collectNode(it, resolved, layout, bindings, nowMillis, typingState, commands) }
        return commands
    }

    private fun collectNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        bindings: UiBindingContext,
        nowMillis: Long,
        typingState: UiTypingState,
        commands: MutableList<UiRenderCommand>,
    ) {
        val style = resolved[node]
        val layoutNode = layout[node]

        val isFramebuffer = layoutNode.needsFramebuffer
        val baseFilter = if (isFramebuffer) UiFilterChain.Empty else style.filter
        val localOpacity = if (isFramebuffer) 1f else style.opacity

        if (style.backdropFilter.effects.isNotEmpty()) {
            commands += DrawBackdropFilterCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                filter = style.backdropFilter, opacity = style.opacity,
                transform = layoutNode.worldTransform, backfaceVisibility = style.backfaceVisibility
            )
        }

        val visibleShadows = if (node is TextNode) emptyList() else style.shadows.filterNot { it.inset }
        if (visibleShadows.isNotEmpty()) {
            commands += DrawShadowCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                shadows = visibleShadows, opacity = style.opacity,
                transform = layoutNode.worldTransform, filter = baseFilter, backfaceVisibility = style.backfaceVisibility
            )
        }

        if (isFramebuffer) {
            commands += BeginLayerCommand(
                node = node, rect = layoutNode.rect, radius = style.border.radius,
                transform = layoutNode.worldTransform, filter = style.filter,
                backdropFilter = style.backdropFilter, backfaceVisibility = style.backfaceVisibility,
                opacity = style.opacity,
            )
        }

        if (style.background != UiPaint.None || style.border.width != UiInsets.Zero) {
            commands += DrawBoxCommand(
                node = node, rect = layoutNode.rect, paint = style.background.resolve(bindings),
                border = style.border, shadows = emptyList(), opacity = localOpacity, tint = style.tint,
                transform = layoutNode.worldTransform, renderToFramebuffer = false,
                fit = style.imageFit, slice = style.imageSlice, filter = baseFilter, backfaceVisibility = style.backfaceVisibility
            )
        }

        val pushedClip = style.clip || style.input.scrollable
        if (pushedClip) commands += PushClipCommand(node, layoutNode.content)

        collectNodeContent(
            node,
            style,
            localOpacity,
            layoutNode,
            layout,
            baseFilter,
            bindings,
            nowMillis,
            typingState,
            commands,
        )

        node.children
            .filterNot { it is PopupNode }
            .sortedBy { resolved[it].layer }
            .forEach { collectNode(it, resolved, layout, bindings, nowMillis, typingState, commands) }

        if (pushedClip) commands += PopClipCommand(node)
        if (style.input.scrollable) appendScrollbars(node, layoutNode, style, localOpacity, bindings, commands)
        if (isFramebuffer) commands += EndLayerCommand(node)
    }

    private fun collectNodeContent(
        node: UiNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
        filter: UiFilterChain,
        bindings: UiBindingContext,
        nowMillis: Long,
        typingState: UiTypingState,
        commands: MutableList<UiRenderCommand>
    ) {
        val contentTransform = layoutNode.worldTransform * UiMatrix4.translation(
            layoutNode.content.x - layoutNode.rect.x,
            layoutNode.content.y - layoutNode.rect.y,
            0f
        )
        val backface = style.backfaceVisibility

        when (node) {
            is TextNode -> {
                val fullContent = node.content.resolve(bindings)
                val visibleContent = fullContent.visibleBy(
                    style.typing,
                    typingState.elapsed(node, style.typing, fullContent.text, nowMillis),
                )
                val textString = visibleContent.text
                val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height
                val widgetMetrics = node.inlineWidgetMetrics(layout)
                val fullLayout = UiTextLayouter.layout(
                    fullContent.toRichText(widgetMetrics),
                    layoutNode.content.width,
                    textHeight,
                    style.textWrap,
                    style.textAlign,
                    style.fontSize,
                    style.fontFamily,
                    lineSpacing = style.lineSpacing,
                    spaceWidth = style.spaceWidth,
                )
                val textLayout = if (style.typing == null) {
                    fullLayout
                } else {
                    UiTextLayouter.visibleTextPrefix(fullLayout, textString.length, style.fontSize, style.fontFamily)
                }

                commands += DrawTextCommand(
                    node, layoutNode.content, textString, style.foreground, opacity, contentTransform,
                    filter, style.textWrap, style.textAlign, style.fontSize,
                    style.fontFamily, style.textEffectsWithShadows(),
                    textLayout,
                    layoutNode.scrollOffset, node.hoveredLink, backface
                )
            }
            is ImageNode -> commands += DrawImageCommand(node, layoutNode.content, node.source.resolve(bindings), opacity, style.tint, contentTransform, false, style.imageFit, style.imageSlice, filter, backface)
            is ItemNode -> commands += DrawItemCommand(node, layoutNode.content, node.item.resolve(bindings), opacity, contentTransform, filter, backface)
            is EntityNode -> commands += DrawEntityCommand(node, layoutNode.content, node.entity.resolve(bindings), opacity, contentTransform, false, filter, backface)
            is CanvasNode -> commands += DrawCanvasCommand(node, layoutNode.content, node.renderer, opacity, contentTransform, false, filter, backface)
            is SliderNode -> commands += sliderCommand(node, style, opacity, layoutNode, contentTransform, filter, bindings, backface)
            is CheckboxNode -> commands += checkboxCommand(node, style, opacity, layoutNode, contentTransform, filter, bindings, backface)
            is TextFieldNode -> appendTextFieldCommands(node, style, opacity, layoutNode, contentTransform, filter, backface, commands)
        }
    }

    private fun sliderCommand(
        node: SliderNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        transform: UiMatrix4,
        filter: UiFilterChain,
        bindings: UiBindingContext,
        backface: UiBackfaceVisibility,
    ): DrawSliderCommand {
        val slider = style.slider
        val thumb = slider.thumbSize ?: UiSize(12.px, 12.px)
        return DrawSliderCommand(
            node = node,
            rect = layoutNode.content,
            value = node.value,
            fraction = node.fraction,
            trackThickness = (slider.trackThickness ?: 4.px).resolve(layoutNode.content.height),
            trackPaint = slider.trackPaint.resolve(bindings, UiPaint.Color(UiColor(0.24f, 0.27f, 0.32f, 1f))),
            activeTrackPaint = slider.activeTrackPaint.resolve(bindings, UiPaint.Color(UiColor(0.36f, 0.62f, 0.95f, 1f))),
            thumbPaint = slider.thumbPaint.resolve(bindings, UiPaint.Color(UiColor.White)),
            thumbBorder = slider.thumbBorder ?: UiBorder(UiInsets.all(1.px), UiColor(0.06f, 0.07f, 0.08f, 0.45f), 6f),
            thumbWidth = thumb.width.resolve(layoutNode.content.width, 12f),
            thumbHeight = thumb.height.resolve(layoutNode.content.height, 12f),
            radius = slider.radius ?: 4f,
            opacity = opacity,
            transform = transform,
            filter = filter,
            backfaceVisibility = backface,
        )
    }

    private fun checkboxCommand(
        node: CheckboxNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        transform: UiMatrix4,
        filter: UiFilterChain,
        bindings: UiBindingContext,
        backface: UiBackfaceVisibility,
    ): DrawCheckboxCommand {
        val checkbox = style.checkbox
        return DrawCheckboxCommand(
            node = node,
            rect = layoutNode.content,
            checked = node.checked,
            variant = checkbox.variant ?: node.variant,
            activePaint = checkbox.activePaint.resolve(bindings, UiPaint.Color(UiColor(0.36f, 0.62f, 0.95f, 1f))),
            markPaint = checkbox.markPaint.resolve(bindings, UiPaint.Color(UiColor.White)),
            opacity = opacity,
            transform = transform,
            filter = filter,
            backfaceVisibility = backface,
        )
    }

    private fun appendTextFieldCommands(
        node: TextFieldNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        transform: UiMatrix4,
        filter: UiFilterChain,
        backface: UiBackfaceVisibility,
        commands: MutableList<UiRenderCommand>,
    ) {
        val text = node.value
        val visible = text.ifEmpty { node.placeholder }
        val wrap = style.textWrap && node.multiline && textFieldWidthConstrained(style, node, layoutNode.content.width)
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height
        val editLayout = textFieldEditLayout(node, style, layoutNode)
        val displayLayout = if (text.isEmpty()) {
            UiTextLayouter.layout(
                visible,
                layoutNode.content.width,
                textHeight,
                wrap,
                style.textAlign,
                style.fontSize,
                style.fontFamily,
                lineSpacing = style.lineSpacing,
                spaceWidth = style.spaceWidth,
            )
        } else {
            editLayout
        }
        val field = style.textField
        commands += DrawTextFieldChromeCommand(
            node = node,
            rect = layoutNode.content,
            layout = editLayout,
            scrollOffset = layoutNode.scrollOffset,
            caretIndex = node.caret,
            selectionStart = node.selectionStart,
            selectionEnd = node.selectionEnd,
            caretColor = field.caretColor ?: style.foreground,
            selectionColor = field.selectionColor ?: UiColor(0.28f, 0.54f, 0.95f, 0.35f),
            lineNumberColor = field.lineNumberColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.78f),
            inlayHintColor = field.inlayHintColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.55f),
            showCaret = UiState.FOCUS in node.states,
            showLineNumbers = field.lineNumbers == true,
            showInlayHints = field.inlayHints == true,
            placeholder = node.placeholder,
            opacity = opacity,
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            transform = transform,
            filter = filter,
            backfaceVisibility = backface,
        )
        commands += DrawTextCommand(
            node = node,
            rect = layoutNode.content,
            text = visible,
            color = if (text.isEmpty()) field.inlayHintColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.65f) else style.foreground,
            opacity = opacity,
            transform = transform,
            filter = filter,
            wrap = wrap,
            align = style.textAlign,
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            textEffects = style.textEffects,
            layout = displayLayout,
            scrollOffset = layoutNode.scrollOffset,
            hoveredLink = null,
            backfaceVisibility = backface,
        )
    }

    private fun appendScrollbars(
        node: UiNode,
        layoutNode: UiLayoutNode,
        style: ComputedStyle,
        opacity: Float,
        bindings: UiBindingContext,
        commands: MutableList<UiRenderCommand>,
    ) {
        val verticalStyle = style.scrollbar.resolved(layoutNode.scrollArea.width)
        val horizontalStyle = style.scrollbar.resolved(layoutNode.scrollArea.height)
        val hasVerticalScrollbar = layoutNode.scrollRange.y > 0f && layoutNode.scrollArea.height > verticalStyle.gutter
        val hasHorizontalScrollbar = layoutNode.scrollRange.x > 0f && layoutNode.scrollArea.width > horizontalStyle.gutter
        if (hasVerticalScrollbar) {
            val horizontalReserve = if (hasHorizontalScrollbar) horizontalStyle.gutter else 0f
            val trackHeight = layoutNode.scrollArea.height - verticalStyle.margin * 2f - horizontalReserve
            if (trackHeight > 0f) {
                val track = UiRect(
                    x = layoutNode.scrollArea.x - layoutNode.rect.x + layoutNode.scrollArea.width - verticalStyle.thickness - verticalStyle.margin,
                    y = layoutNode.scrollArea.y - layoutNode.rect.y + verticalStyle.margin,
                    width = verticalStyle.thickness,
                    height = trackHeight,
                )
                val contentHeight = layoutNode.content.height + layoutNode.scrollRange.y
                val thumbHeight = maxOf(verticalStyle.minThumbSize, track.height * layoutNode.content.height / contentHeight)
                val thumbY = track.y + (track.height - thumbHeight) * (layoutNode.scrollOffset.y / layoutNode.scrollRange.y)
                commands += DrawScrollbarCommand(
                    node = node,
                    track = track,
                    thumb = track.copy(y = thumbY, height = thumbHeight),
                    orientation = ScrollbarOrientation.VERTICAL,
                    trackPaint = verticalStyle.track.paint.resolve(bindings, UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))),
                    trackBorder = verticalStyle.track.border ?: UiBorder(radius = verticalStyle.track.radius ?: 3.5f),
                    trackFit = verticalStyle.track.fit ?: UiImageFit.STRETCH,
                    trackSlice = verticalStyle.track.slice ?: UiInsets.all(4.px),
                    thumbPaint = verticalStyle.thumb.paint.resolve(bindings, UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, 0.9f))),
                    thumbBorder = verticalStyle.thumb.border ?: UiBorder(radius = verticalStyle.thumb.radius ?: 3.5f),
                    thumbFit = verticalStyle.thumb.fit ?: UiImageFit.STRETCH,
                    thumbSlice = verticalStyle.thumb.slice ?: UiInsets.all(4.px),
                    opacity = opacity,
                    transform = layoutNode.worldTransform,
                )
            }
        }
        if (hasHorizontalScrollbar) {
            val verticalReserve = if (hasVerticalScrollbar) verticalStyle.gutter else 0f
            val trackWidth = layoutNode.scrollArea.width - horizontalStyle.margin * 2f - verticalReserve
            if (trackWidth > 0f) {
                val track = UiRect(
                    x = layoutNode.scrollArea.x - layoutNode.rect.x + horizontalStyle.margin,
                    y = layoutNode.scrollArea.y - layoutNode.rect.y + layoutNode.scrollArea.height - horizontalStyle.thickness - horizontalStyle.margin,
                    width = trackWidth,
                    height = horizontalStyle.thickness,
                )
                val contentWidth = layoutNode.content.width + layoutNode.scrollRange.x
                val thumbWidth = maxOf(horizontalStyle.minThumbSize, track.width * layoutNode.content.width / contentWidth)
                val thumbX = track.x + (track.width - thumbWidth) * (layoutNode.scrollOffset.x / layoutNode.scrollRange.x)
                commands += DrawScrollbarCommand(
                    node = node,
                    track = track,
                    thumb = track.copy(x = thumbX, width = thumbWidth),
                    orientation = ScrollbarOrientation.HORIZONTAL,
                    trackPaint = horizontalStyle.track.paint.resolve(bindings, UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))),
                    trackBorder = horizontalStyle.track.border ?: UiBorder(radius = horizontalStyle.track.radius ?: 3.5f),
                    trackFit = horizontalStyle.track.fit ?: UiImageFit.STRETCH,
                    trackSlice = horizontalStyle.track.slice ?: UiInsets.all(4.px),
                    thumbPaint = horizontalStyle.thumb.paint.resolve(bindings, UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, 0.82f))),
                    thumbBorder = horizontalStyle.thumb.border ?: UiBorder(radius = horizontalStyle.thumb.radius ?: 3.5f),
                    thumbFit = horizontalStyle.thumb.fit ?: UiImageFit.STRETCH,
                    thumbSlice = horizontalStyle.thumb.slice ?: UiInsets.all(4.px),
                    opacity = opacity,
                    transform = layoutNode.worldTransform,
                )
            }
        }
    }
}

private fun UiNode.popupDescendants(): List<PopupNode> {
    val result = mutableListOf<PopupNode>()
    fun visit(node: UiNode) {
        for (child in node.children) {
            if (child is PopupNode) result += child
            visit(child)
        }
    }
    visit(this)
    return result
}

private fun TextNode.inlineWidgetMetrics(layout: UiLayoutResult): Map<String, UiInlineWidgetMetrics> {
    return children.mapNotNull { child ->
        val id = child.id ?: return@mapNotNull null
        val rect = layout.nodes[child]?.rect ?: return@mapNotNull null
        id to UiInlineWidgetMetrics(rect.width, rect.height)
    }.toMap()
}

private fun ComputedStyle.textEffectsWithShadows(): List<UiTextEffect> {
    val textShadows = shadows.filterNot { it.inset }.map { it.toTextShadow() }
    return if (textShadows.isEmpty()) textEffects else textEffects + textShadows
}

private fun UiShadow.toTextShadow() = TextShadow(
    offsetX = offset.x,
    offsetY = offset.y,
    blur = blur,
    color = color,
)

class UiTypingState {
    private val starts = linkedMapOf<String, TypingStart>()

    fun elapsed(node: UiNode, typing: UiTyping?, text: String, nowMillis: Long): Long {
        if (typing == null) {
            starts.remove(UiNodeKeys.key(node))
            return Long.MAX_VALUE
        }
        val key = UiNodeKeys.key(node)
        val signature = TypingSignature(text, typing)
        val current = starts[key]
        if (current == null || current.signature != signature) {
            starts[key] = TypingStart(signature, nowMillis)
            return 0L
        }
        return (nowMillis - current.startedAtMillis).coerceAtLeast(0L)
    }

    private data class TypingStart(
        val signature: TypingSignature,
        val startedAtMillis: Long,
    )

    private data class TypingSignature(
        val text: String,
        val typing: UiTyping,
    )
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

private fun UiPaint?.resolve(bindings: UiBindingContext, fallback: UiPaint): UiResolvedPaint =
    (this ?: fallback).resolve(bindings)

data class UiHit(
    val node: UiNode,
    val localX: Float,
    val localY: Float,
    val link: String? = null,
)

class UiHitTester {
    fun hitTest(resolved: ResolvedUiTree, layout: UiLayoutResult, x: Float, y: Float): UiHit? {
        val popups = resolved.root.popupDescendants()
            .sortedWith(compareBy<PopupNode> { resolved[it].layer }.thenBy { layout[it].rect.y })
        for (popup in popups.asReversed()) {
            hitNode(popup, resolved, layout, x, y, ancestorClip = null)?.let { return it }
        }
        return hitNode(resolved.root, resolved, layout, x, y, ancestorClip = null)
    }

    private fun hitNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        x: Float,
        y: Float,
        ancestorClip: UiRect?,
    ): UiHit? {
        val children = node.children.sortedWith(compareBy<UiNode> { resolved[it].layer }.thenBy { layout[it].rect.y })
        val layoutNode = layout[node]
        val effectiveClip = ancestorClip.intersect(layoutNode.clip)
        for (child in children.filterIsInstance<PopupNode>().asReversed()) {
            hitNode(child, resolved, layout, x, y, ancestorClip = ancestorClip)?.let { return it }
        }
        for (child in children.filterNot { it is PopupNode }.asReversed()) {
            hitNode(child, resolved, layout, x, y, ancestorClip = effectiveClip)?.let { return it }
        }
        val style = resolved[node]
        if (UiState.DISABLED in node.states) return null
        if (!style.input.hoverable && !style.input.clickable && !style.input.focusable && !style.input.draggable && !style.input.scrollable) {
            return null
        }
        ancestorClip?.let { clip ->
            if (!clip.contains(x, y)) return null
        }
        if (!layoutNode.inputQuadContains(x, y)) return null
        val inverse = layoutNode.inputTransform.inverse() ?: return null
        val local = inverse.transform(x, y, 0f)
        val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
        if (!rect.contains(local.x, local.y)) return null
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

private fun UiRect?.intersect(other: UiRect?): UiRect? {
    if (this == null) return other
    if (other == null) return this
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
}
