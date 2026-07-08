package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarThumbNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
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
        collectNode(resolved.root, resolved, layout, sink, activeClip = null)
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
    ) {
        val style = resolved[node]
        val layoutNode = layout[node]
        val isFramebuffer = layoutNode.needsFramebuffer
        val baseFilter = if (isFramebuffer) UiFilterChain.Empty else style.filter
        val localOpacity = if (isFramebuffer) 1f else style.opacity
        val visibleShadows = if (node is TextFieldNode) {
            emptyList()
        } else {
            style.shadows.filterNot { it.inset }
        }
        val canCullNode = activeClip != null &&
                !isFramebuffer &&
                visibleShadows.isEmpty() &&
                style.backdropFilter.effects.isEmpty() &&
                style.filter == UiFilterChain.Empty &&
                style.transform == DirectLayoutTransform
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
    ) {
        appendBackgroundCommand(node, style, layoutNode, localOpacity, baseFilter, commands)

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
                collectNode(child, resolved, layout, commands, activeClip = childClip)
            }
        }

        if (pushedClip) commands += PopClipCommand(node)

        // Framework-synthesized scrollbars render after the content clip is popped so they sit
        // in the gutter, on top of content, clipped only by the container's ancestors.
        layout.scrollbars[node]?.forEach { scrollbar ->
            if (scrollbar in layout.nodes) {
                collectNode(scrollbar, resolved, layout, commands, activeClip = activeClip)
            }
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
        val contentTransform = layoutNode.worldTransform * UiMatrix4.translation(
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

            is ImageNode -> commands += DrawImageCommand(
                node,
                layoutNode.content,
                node.source,
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
                node.item,
                opacity,
                contentTransform,
                filter,
                backface
            )

            is EntityNode -> commands += DrawEntityCommand(
                node,
                layoutNode.content,
                node.entity,
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
        val paint = style.background.resolve()
        val hasBorder = style.border.width != UiInsets.Zero
        if (paint == UiResolvedPaint.None && !hasBorder) return
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
            commands += DrawBoxCommand(
                node = node,
                rect = UiRect(0f, 0f, box.width, box.height),
                paint = paint,
                border = border,
                shadows = emptyList(),
                opacity = opacity,
                tint = style.tint,
                transform = layoutNode.worldTransform * UiMatrix4.translation(box.x, box.y, 0f),
                renderToFramebuffer = false,
                fit = style.imageFit,
                slice = style.imageSlice,
                filter = filter,
                backfaceVisibility = style.backfaceVisibility,
                phase = UiRenderPhase.BACKGROUND,
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

    private fun sliderCommand(
        node: SliderNode,
        style: UiComputedStyle,
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
        style: UiComputedStyle,
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
        style: UiComputedStyle,
        opacity: Float,
        layoutNode: UiLayoutNode,
        layout: UiLayoutResult,
        transform: UiMatrix4,
        filter: UiFilterChain,
        backface: UiBackfaceVisibility,
        commands: UiRenderSink,
    ) {
        val text = node.value
        val visible = text.ifEmpty { node.placeholder }
        val fontSize = style.fontSize
        val wrap = style.textWrap && node.multiline && textFieldWidthConstrained(style, node, layoutNode.content.width)
        val textHeight = if (style.scrollable) Float.POSITIVE_INFINITY else layoutNode.content.height
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

}

private fun UiNode.inlineWidgetMetrics(layout: UiLayoutResult): Map<String, UiInlineWidgetMetrics> {
    layout.nodes[this]?.inlineWidgetMetrics()?.takeIf { it.isNotEmpty() }?.let { return it }
    return children.mapNotNull { child ->
        val id = child.id ?: return@mapNotNull null
        val rect = layout.nodes[child]?.rect ?: return@mapNotNull null
        id to UiInlineWidgetMetrics(rect.width, rect.height)
    }.toMap()
}

private fun UiComputedStyle.textEffectsWithShadows(): List<UiTextEffect> {
    val textShadows = shadows.filterNot { it.inset }.map { it.toTextShadow() }
    return if (textShadows.isEmpty()) textEffects else textEffects + textShadows
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

private fun UiPaint?.resolve(fallback: UiPaint): UiResolvedPaint =
    (this ?: fallback).resolve()

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
                    val children = current.children
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
                    if (UiState.DISABLED in current.effectiveStates()) continue
                    if (!style.input.hoverable &&
                        !style.input.clickable &&
                        !style.focusable &&
                        !style.input.draggable &&
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
                    val children = current.children.filter { it in layout.nodes }.sortedBy { resolved[it].layer }
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
        is SpanNode, is ImageNode, is ItemNode, is EntityNode,
        is SliderNode, is CheckboxNode, is TextFieldNode, is ScrollbarNode, is ScrollbarThumbNode,
            -> true

        else -> style.background != UiPaint.None || style.border.width != UiInsets.Zero || style.shape != null
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
