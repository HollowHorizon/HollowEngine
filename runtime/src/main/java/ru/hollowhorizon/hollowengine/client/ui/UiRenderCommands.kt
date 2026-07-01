package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutResult
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.layout.inlineWidgetMetrics
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*
import ru.hollowhorizon.hollowengine.client.ui.effects.Shadow as TextShadow

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
    val hoveredLink: String?,
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
    val carets: List<UiTextCaret>,
    val caretVisibilityRevision: Long,
    val textOffset: Float,
    val caretColor: UiColor,
    val selectionColor: UiColor,
    val lineNumberColor: UiColor,
    val inlayHintColor: UiColor,
    val diagnosticErrorColor: UiColor,
    val diagnosticWarningColor: UiColor,
    val diagnosticInfoColor: UiColor,
    val showCaret: Boolean,
    val showLineNumbers: Boolean,
    val showInlayHints: Boolean,
    val diagnostics: List<UiTextDiagnostic>,
    val inlayHints: List<UiInlayHint>,
    val placeholder: String,
    val opacity: Float,
    val fontSize: Float,
    val fontFamily: String?,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
) : UiRenderCommand

class UiCommandRenderer {
    fun collect(
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        nowMillis: Long = 0L,
        typingState: UiTypingState = UiTypingState(),
    ): List<UiRenderCommand> {
        val commands = mutableListOf<UiRenderCommand>()
        collectNode(resolved.root, resolved, layout, nowMillis, typingState, commands)
        layout.popupNodes
            .sortedBy { resolved[it].layer }
            .forEach { collectNode(it, resolved, layout, nowMillis, typingState, commands) }
        return commands
    }

    private fun collectNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayoutResult,
        nowMillis: Long,
        typingState: UiTypingState,
        commands: MutableList<UiRenderCommand>,
    ) {
        val stack = ArrayDeque<RenderCollectTask>()
        stack.add(RenderCollectTask.Enter(node, null))
        while (stack.isNotEmpty()) {
            when (val task = stack.removeLast()) {
                is RenderCollectTask.Enter -> {
                    val current = task.node
                    val style = resolved[current]
                    val layoutNode = layout[current]
                    val isFramebuffer = layoutNode.needsFramebuffer
                    val baseFilter = if (isFramebuffer) UiFilterChain.Empty else style.filter
                    val localOpacity = if (isFramebuffer) 1f else style.opacity
                    val visibleShadows = if (current is TextNode || current is TextFieldNode) {
                        emptyList()
                    } else {
                        style.shadows.filterNot { it.inset }
                    }
                    val canCullNode = task.activeClip != null &&
                            current !is PopupNode &&
                            !isFramebuffer &&
                            visibleShadows.isEmpty() &&
                            style.backdropFilter.effects.isEmpty() &&
                            style.filter == UiFilterChain.Empty &&
                            style.transform == DirectLayoutTransform
                    val cullNodeCommands =
                        task.activeClip?.let { canCullNode && !layoutNode.rect.intersectsVisible(it) } == true
                    val pushedClip = (style.clip && style.clipShape == null) || style.input.scrollable

                    if (cullNodeCommands && pushedClip) continue

                    if (!cullNodeCommands && style.backdropFilter.effects.isNotEmpty()) {
                        commands += DrawBackdropFilterCommand(
                            node = current, rect = layoutNode.rect, radius = style.border.radius,
                            filter = style.backdropFilter, opacity = style.opacity,
                            transform = layoutNode.worldTransform, backfaceVisibility = style.backfaceVisibility
                        )
                    }

                    if (!cullNodeCommands && visibleShadows.isNotEmpty()) {
                        commands += DrawShadowCommand(
                            node = current, rect = layoutNode.rect, radius = style.border.radius,
                            shadows = visibleShadows, opacity = style.opacity,
                            transform = layoutNode.worldTransform, filter = baseFilter,
                            backfaceVisibility = style.backfaceVisibility
                        )
                    }

                    if (!cullNodeCommands && isFramebuffer) {
                        commands += BeginLayerCommand(
                            node = current, rect = layoutNode.rect, radius = style.border.radius,
                            clipShape = style.clipShape.takeIf { style.clip },
                            transform = layoutNode.worldTransform, filter = style.filter,
                            backdropFilter = style.backdropFilter, backfaceVisibility = style.backfaceVisibility,
                            opacity = style.opacity,
                        )
                    }

                    if (!cullNodeCommands) {
                        appendBackgroundCommand(current, style, layoutNode, localOpacity, baseFilter, commands)
                    }

                    if (!cullNodeCommands && pushedClip) {
                        commands += PushClipCommand(
                            current,
                            layoutNode.content.localTo(layoutNode.rect),
                            layoutNode.worldTransform,
                        )
                    }

                    if (!cullNodeCommands) {
                        collectNodeContent(
                            current,
                            style,
                            localOpacity,
                            layoutNode,
                            layout,
                            baseFilter,
                            nowMillis,
                            typingState,
                            commands,
                        )
                    }

                    val childClip = when {
                        cullNodeCommands -> task.activeClip
                        !pushedClip -> task.activeClip
                        task.activeClip == null -> layoutNode.content.takeIf { it.hasVisibleArea() }
                        else -> task.activeClip.visibleIntersection(layoutNode.content)
                    }
                    if (!cullNodeCommands) {
                        stack.add(
                            RenderCollectTask.Exit(
                                current,
                                layoutNode,
                                style,
                                localOpacity,
                                pushedClip,
                                isFramebuffer
                            )
                        )
                    }
                    if (!pushedClip || childClip != null) {
                        val children = current.children
                            .filterNot { it is PopupNode }
                            .filter { it in layout.nodes }
                            .sortedBy { resolved[it].layer }
                        for (index in children.indices.reversed()) {
                            stack.add(RenderCollectTask.Enter(children[index], childClip))
                        }
                    }
                }

                is RenderCollectTask.Exit -> {
                    if (task.pushedClip) commands += PopClipCommand(task.node)
                    if (task.style.input.scrollable) {
                        appendScrollbars(
                            task.node,
                            task.layoutNode,
                            task.style,
                            task.localOpacity,
                            commands
                        )
                    }
                    if (task.isFramebuffer) commands += EndLayerCommand(task.node)
                }
            }
        }
    }

    private fun collectNodeContent(
        node: UiNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
        filter: UiFilterChain,
        nowMillis: Long,
        typingState: UiTypingState,
        commands: MutableList<UiRenderCommand>,
    ) {
        val contentTransform = layoutNode.worldTransform * UiMatrix4.translation(
            layoutNode.content.x - layoutNode.rect.x,
            layoutNode.content.y - layoutNode.rect.y,
            0f
        )
        val backface = style.backfaceVisibility

        when (node) {
            is TextNode -> {
                val fullContent = node.content.resolve()
                val visibleContent = fullContent.visibleBy(
                    style.typing,
                    typingState.elapsed(node, style.typing, fullContent.text, nowMillis),
                )
                val textString = visibleContent.text
                val fullLayout = layoutNode.textLayout ?: fallbackTextLayout(node, style, layoutNode, layout)
                val textLayout = if (style.typing == null) {
                    fullLayout
                } else {
                    UiTextLayouter.visibleTextPrefix(fullLayout, textString.length, style.fontSize, style.fontFamily)
                }

                commands += DrawTextCommand(
                    node, layoutNode.content, textString, style.foreground, opacity, contentTransform,
                    filter, style.textWrap, style.textOverflow, style.textAlign, style.fontSize,
                    style.fontFamily, style.textEffectsWithShadows(),
                    textLayout,
                    layoutNode.scrollOffset, node.hoveredLink, backface
                )
            }

            is ImageNode -> commands += DrawImageCommand(
                node,
                layoutNode.content,
                node.source.resolve(),
                opacity,
                style.tint,
                contentTransform,
                false,
                style.imageFit,
                style.imageSlice,
                filter,
                backface
            )

            is ItemNode -> commands += DrawItemCommand(
                node,
                layoutNode.content,
                node.item.resolve(),
                opacity,
                contentTransform,
                filter,
                backface
            )

            is EntityNode -> commands += DrawEntityCommand(
                node,
                layoutNode.content,
                node.entity.resolve(),
                opacity,
                contentTransform,
                false,
                filter,
                backface
            )

            is CanvasNode -> commands += DrawCanvasCommand(
                node,
                layoutNode.content,
                node.renderer,
                opacity,
                contentTransform,
                false,
                filter,
                backface
            )

            is SliderNode -> commands += sliderCommand(
                node,
                style,
                opacity,
                layoutNode,
                contentTransform,
                filter,
                backface
            )

            is CheckboxNode -> commands += checkboxCommand(
                node,
                style,
                opacity,
                layoutNode,
                contentTransform,
                filter,
                backface
            )

            is TextFieldNode -> appendTextFieldCommands(
                node,
                style,
                opacity,
                layoutNode,
                layout,
                contentTransform,
                filter,
                backface,
                commands,
            )
        }
    }

    private fun appendBackgroundCommand(
        node: UiNode,
        style: ComputedStyle,
        layoutNode: UiLayoutNode,
        opacity: Float,
        filter: UiFilterChain,
        commands: MutableList<UiRenderCommand>,
    ) {
        val shape = style.shape
        if (shape != null) {
            val fill = (style.shapeFill ?: style.background).resolve()
            val strokePaint =
                style.shapeStroke ?: style.border.takeIf { it.width != UiInsets.Zero }?.let { UiPaint.Color(it.color) }
            val stroke = strokePaint.resolve(UiPaint.None)
            val strokeWidth = (style.shapeStrokeWidth ?: style.border.width.left).resolve(layoutNode.rect.width)
            if (fill != UiResolvedPaint.None || stroke != UiResolvedPaint.None && strokeWidth > 0f) {
                commands += DrawShapeCommand(
                    node = node,
                    rect = layoutNode.rect,
                    shape = shape,
                    fill = fill,
                    stroke = stroke,
                    strokeWidth = strokeWidth,
                    opacity = opacity,
                    transform = layoutNode.worldTransform,
                    filter = filter,
                    backfaceVisibility = style.backfaceVisibility,
                    phase = UiRenderPhase.BACKGROUND,
                )
            }
            return
        }
        if (style.background == UiPaint.None && style.border.width == UiInsets.Zero) return
        commands += DrawBoxCommand(
            node = node, rect = layoutNode.rect, paint = style.background.resolve(),
            border = style.border, shadows = emptyList(), opacity = opacity, tint = style.tint,
            transform = layoutNode.worldTransform, renderToFramebuffer = false,
            fit = style.imageFit, slice = style.imageSlice, filter = filter,
            backfaceVisibility = style.backfaceVisibility, phase = UiRenderPhase.BACKGROUND
        )
    }

    private fun fallbackTextLayout(
        node: TextNode,
        style: ComputedStyle,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
    ): UiTextLayout {
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height
        return UiTextLayouter.layout(
            node.content.resolve().toRichText(node.inlineWidgetMetrics(layout)),
            layoutNode.content.width,
            textHeight,
            style.textWrap,
            style.textAlign,
            style.fontSize,
            style.fontFamily,
            lineSpacing = style.lineSpacing,
            spaceWidth = style.spaceWidth,
        )
    }

    private fun sliderCommand(
        node: SliderNode,
        style: ComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        transform: UiMatrix4,
        filter: UiFilterChain,
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
            trackPaint = slider.trackPaint.resolve(UiPaint.Color(UiColor(0.24f, 0.27f, 0.32f, 1f))),
            activeTrackPaint = slider.activeTrackPaint.resolve(UiPaint.Color(UiColor(0.36f, 0.62f, 0.95f, 1f))),
            thumbPaint = slider.thumbPaint.resolve(UiPaint.Color(UiColor.White)),
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
        backface: UiBackfaceVisibility,
    ): DrawCheckboxCommand {
        val checkbox = style.checkbox
        return DrawCheckboxCommand(
            node = node,
            rect = layoutNode.content,
            checked = node.checked,
            variant = checkbox.variant ?: node.variant,
            activePaint = checkbox.activePaint.resolve(UiPaint.Color(UiColor(0.36f, 0.62f, 0.95f, 1f))),
            markPaint = checkbox.markPaint.resolve(UiPaint.Color(UiColor.White)),
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
        layout: UiLayoutResult,
        transform: UiMatrix4,
        filter: UiFilterChain,
        backface: UiBackfaceVisibility,
        commands: MutableList<UiRenderCommand>,
    ) {
        val text = node.value
        val visible = text.ifEmpty { node.placeholder }
        val fontSize = style.fontSize
        val wrap = style.textWrap && node.multiline && textFieldWidthConstrained(style, node, layoutNode.content.width)
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height
        val widgetMetrics = node.inlineWidgetMetrics(layout)
        val editLayout = textFieldEditLayout(node, style, layoutNode, widgetMetrics)
        val displayLayout = if (text.isEmpty()) {
            UiTextLayouter.layout(
                visible,
                textFieldTextWidth(node, style, layoutNode),
                textHeight,
                wrap,
                style.textAlign,
                fontSize,
                style.fontFamily,
                lineSpacing = style.lineSpacing,
                spaceWidth = style.spaceWidth,
            )
        } else {
            textFieldDisplayLayout(node, style, layoutNode, widgetMetrics)
        }
        val field = style.textField
        val textOffset = textFieldTextOffset(node, style)
        commands += PushClipCommand(
            node = node,
            rect = UiRect(
                textOffset,
                0f,
                (layoutNode.content.width - textOffset).coerceAtLeast(0f),
                layoutNode.content.height,
            ),
            transform = transform,
        )
        commands += DrawTextCommand(
            node = node,
            rect = layoutNode.content,
            text = visible,
            color = if (text.isEmpty()) field.inlayHintColor ?: UiColor(
                0.56f,
                0.6f,
                0.66f,
                0.65f
            ) else style.foreground,
            opacity = opacity,
            transform = transform * UiMatrix4.translation(textOffset, 0f, 0f),
            filter = filter,
            wrap = wrap,
            overflow = UiTextOverflow.HIDDEN,
            align = style.textAlign,
            fontSize = fontSize,
            fontFamily = style.fontFamily,
            textEffects = style.textEffectsWithShadows(),
            layout = displayLayout,
            scrollOffset = layoutNode.scrollOffset,
            hoveredLink = null,
            backfaceVisibility = backface,
        )
        commands += PopClipCommand(node)
        commands += DrawTextFieldChromeCommand(
            node = node,
            rect = layoutNode.content,
            layout = editLayout,
            scrollOffset = layoutNode.scrollOffset,
            carets = node.caretRanges.toList(),
            caretVisibilityRevision = node.caretVisibilityRevision,
            textOffset = textOffset,
            caretColor = field.caretColor ?: style.foreground,
            selectionColor = field.selectionColor ?: UiColor(0.28f, 0.54f, 0.95f, 0.35f),
            lineNumberColor = field.lineNumberColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.78f),
            inlayHintColor = field.inlayHintColor ?: UiColor(0.56f, 0.6f, 0.66f, 0.55f),
            diagnosticErrorColor = UiColor(1f, 0.33f, 0.33f, 0.9f),
            diagnosticWarningColor = UiColor(1f, 0.72f, 0.26f, 0.88f),
            diagnosticInfoColor = UiColor(0.38f, 0.66f, 1f, 0.84f),
            showCaret = UiState.FOCUS in node.effectiveStates(),
            showLineNumbers = field.lineNumbers == true,
            showInlayHints = field.inlayHints == true,
            diagnostics = node.diagnostics,
            inlayHints = node.currentInlayHints(),
            placeholder = node.placeholder,
            opacity = opacity,
            fontSize = fontSize,
            fontFamily = style.fontFamily,
            transform = transform,
            filter = filter,
            backfaceVisibility = backface,
        )
    }

    private fun appendScrollbars(
        node: UiNode,
        layoutNode: UiLayoutNode,
        style: ComputedStyle,
        opacity: Float,
        commands: MutableList<UiRenderCommand>,
    ) {
        for (scrollbar in layoutNode.scrollbars) {
            val scrollbarStyle = when (scrollbar.orientation) {
                ScrollbarOrientation.VERTICAL -> style.scrollbar.resolved(layoutNode.scrollArea.width)
                ScrollbarOrientation.HORIZONTAL -> style.scrollbar.resolved(layoutNode.scrollArea.height)
            }
            val thumbOpacity = when (scrollbar.orientation) {
                ScrollbarOrientation.VERTICAL -> 0.9f
                ScrollbarOrientation.HORIZONTAL -> 0.82f
            }
            commands += scrollbarBoxCommand(
                node = node,
                rect = scrollbar.track,
                paint = scrollbarStyle.track.paint.resolve(UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))),
                border = scrollbarStyle.track.border ?: UiBorder(radius = scrollbarStyle.track.radius ?: 3.5f),
                fit = scrollbarStyle.track.fit ?: UiImageFit.STRETCH,
                slice = scrollbarStyle.track.slice ?: UiInsets.all(4.px),
                opacity = opacity,
                transform = layoutNode.worldTransform,
                backfaceVisibility = style.backfaceVisibility,
            )
            commands += scrollbarBoxCommand(
                node = node,
                rect = scrollbar.thumb,
                paint = scrollbarStyle.thumb.paint.resolve(
                    UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, thumbOpacity)),
                ),
                border = scrollbarStyle.thumb.border ?: UiBorder(radius = scrollbarStyle.thumb.radius ?: 3.5f),
                fit = scrollbarStyle.thumb.fit ?: UiImageFit.STRETCH,
                slice = scrollbarStyle.thumb.slice ?: UiInsets.all(4.px),
                opacity = opacity,
                transform = layoutNode.worldTransform,
                backfaceVisibility = style.backfaceVisibility,
            )
        }
    }

    private fun scrollbarBoxCommand(
        node: UiNode,
        rect: UiRect,
        paint: UiResolvedPaint,
        border: UiBorder,
        fit: UiImageFit,
        slice: UiInsets,
        opacity: Float,
        transform: UiMatrix4,
        backfaceVisibility: UiBackfaceVisibility,
    ): DrawBoxCommand {
        return DrawBoxCommand(
            node = node,
            rect = UiRect(0f, 0f, rect.width, rect.height),
            paint = paint,
            border = border,
            shadows = emptyList(),
            opacity = opacity,
            tint = UiColor.White,
            transform = transform * UiMatrix4.translation(rect.x, rect.y, 0f),
            renderToFramebuffer = false,
            fit = fit,
            slice = slice,
            filter = UiFilterChain.Empty,
            backfaceVisibility = backfaceVisibility,
            phase = UiRenderPhase.OVERLAY,
        )
    }
}

private fun UiNode.inlineWidgetMetrics(layout: UiLayoutResult): Map<String, UiInlineWidgetMetrics> {
    layout.nodes[this]?.inlineWidgetMetrics()?.takeIf { it.isNotEmpty() }?.let { return it }
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
    private val starts = WeakHashMap<UiNode, TypingStart>()

    fun elapsed(node: UiNode, typing: UiTyping?, text: String, nowMillis: Long): Long {
        if (typing == null) {
            starts.remove(node)
            return Long.MAX_VALUE
        }
        val signature = TypingSignature(text, typing)
        val current = starts[node]
        if (current == null || current.signature != signature) {
            starts[node] = TypingStart(signature, nowMillis)
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

private sealed interface RenderCollectTask {
    data class Enter(
        val node: UiNode,
        val activeClip: UiRect?,
    ) : RenderCollectTask

    data class Exit(
        val node: UiNode,
        val layoutNode: UiLayoutNode,
        val style: ComputedStyle,
        val localOpacity: Float,
        val pushedClip: Boolean,
        val isFramebuffer: Boolean,
    ) : RenderCollectTask
}

sealed interface UiResolvedPaint {
    data object None : UiResolvedPaint
    data class Color(val color: UiColor) : UiResolvedPaint
    data class LinearGradient(val angleDegrees: Float, val stops: List<UiGradientStop>) : UiResolvedPaint
    data class RadialGradient(val gradient: UiRadialGradient) : UiResolvedPaint
    data class Image(val source: String) : UiResolvedPaint
    data class Shader(val name: String) : UiResolvedPaint
}

private fun UiPaint.resolve(): UiResolvedPaint = when (this) {
    UiPaint.None -> UiResolvedPaint.None
    is UiPaint.Color -> UiResolvedPaint.Color(color)
    is UiPaint.LinearGradient -> UiResolvedPaint.LinearGradient(angleDegrees, stops)
    is UiPaint.RadialGradient -> UiResolvedPaint.RadialGradient(gradient)
    is UiPaint.Image -> UiResolvedPaint.Image(source.resolve())
    is UiPaint.Shader -> UiResolvedPaint.Shader(name.resolve())
}

private fun UiPaint?.resolve(fallback: UiPaint): UiResolvedPaint =
    (this ?: fallback).resolve()

data class UiHit(
    val node: UiNode,
    val localX: Float,
    val localY: Float,
    val link: String? = null,
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
    fun hitTest(resolved: ResolvedUiTree, layout: UiLayoutResult, x: Float, y: Float): UiHit? {
        val popups = layout.popupNodes
            .sortedBy { resolved[it].layer }
        for (popup in popups.asReversed()) {
            hitNode(popup, resolved, layout, x, y)?.let { return it }
        }
        return hitNode(resolved.root, resolved, layout, x, y)
    }

    private fun hitNode(
        node: UiNode,
        resolved: ResolvedUiTree,
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
                    val children = current.children
                        .filter { it in layout.nodes }
                        .sortedBy { resolved[it].layer }

                    stack.add(HitTestTask.Test(current, task.ancestorClip))
                    val normalChildren = children.filterNot { it is PopupNode }
                    for (child in normalChildren) stack.add(HitTestTask.Enter(child, effectiveClip))
                    val popupChildren = children.filterIsInstance<PopupNode>()
                    for (child in popupChildren) stack.add(HitTestTask.Enter(child, task.ancestorClip))
                }

                is HitTestTask.Test -> {
                    val current = task.node
                    val style = resolved[current]
                    if (UiState.DISABLED in current.effectiveStates()) continue
                    if (!style.input.hoverable &&
                        !style.input.clickable &&
                        !style.input.focusable &&
                        !style.input.draggable &&
                        !style.input.scrollable
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
