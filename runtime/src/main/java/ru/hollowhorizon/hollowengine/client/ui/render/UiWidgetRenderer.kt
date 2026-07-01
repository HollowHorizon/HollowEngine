package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldCaretWidth
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxVariant
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextDiagnosticSeverity
import java.util.*
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class UiWidgetRenderer(
    private val imageDrawer: (Float, Float, String, Float, UiMatrix4, UiImageFit, UiFilterChain, UiInsets, UiColor) -> Unit,
    private val markTextBatchDirty: () -> Unit,
    private val flushTextBatch: () -> Unit,
) {
    private val scratchQuads = mutableListOf<UiBatchedQuad>()
    private val scratchTriangles = mutableListOf<UiBatchedTriangle>()
    private val textFieldCaretBlinkStates = WeakHashMap<TextFieldNode, CaretBlinkState>()

    fun drawSlider(command: DrawSliderCommand, transform: UiMatrix4) {
        val width = command.rect.width
        val height = command.rect.height
        val trackY = (height - command.trackThickness) * 0.5f
        drawResolvedPaint(
            width,
            command.trackThickness,
            command.radius,
            command.trackPaint,
            command.opacity,
            transform * UiMatrix4.translation(0f, trackY, 0f),
            command.filter,
        )
        val activeWidth = width * command.fraction
        if (activeWidth > 0f) {
            drawResolvedPaint(
                activeWidth,
                command.trackThickness,
                command.radius,
                command.activeTrackPaint,
                command.opacity,
                transform * UiMatrix4.translation(0f, trackY, 0f),
                command.filter,
            )
        }
        val thumbX = (width * command.fraction - command.thumbWidth * 0.5f).coerceIn(0f, (width - command.thumbWidth).coerceAtLeast(0f))
        val thumbY = (height - command.thumbHeight) * 0.5f
        val thumbTransform = transform * UiMatrix4.translation(thumbX, thumbY, 0f)
        drawResolvedPaint(
            command.thumbWidth,
            command.thumbHeight,
            command.thumbBorder.radius,
            command.thumbPaint,
            command.opacity,
            thumbTransform,
            command.filter,
        )
        val borderWidth = command.thumbBorder.width.left.resolve(command.thumbWidth)
        if (borderWidth > 0f && command.thumbBorder.color.alpha > 0f) {
            scratchTriangles.appendLocalBorder(
                command.thumbWidth,
                command.thumbHeight,
                command.thumbBorder.radius,
                borderWidth,
                command.thumbBorder.color.withOpacity(command.opacity),
                thumbTransform,
            )
        }
        flushScratchTriangles()
    }

    fun drawCheckbox(command: DrawCheckboxCommand, transform: UiMatrix4) {
        when (command.variant) {
            UiCheckboxVariant.SWITCH -> drawSwitch(command, transform)
            UiCheckboxVariant.RADIO -> drawRadio(command, transform)
            UiCheckboxVariant.CHECKBOX -> drawCheckboxBox(command, transform)
        }
        flushScratchTriangles()
    }

    fun drawTextFieldChrome(command: DrawTextFieldChromeCommand, transform: UiMatrix4) {
        appendSelectionQuads(command, transform, scratchQuads)
        flushScratchQuads()
        if (command.showLineNumbers) {
            command.layout.visibleLineItems(command.scrollOffset.y, command.rect.height).forEach { (index, line) ->
                drawPlainText(
                    (index + 1).toString(),
                    0f,
                    line.y - command.scrollOffset.y,
                    command.fontSize,
                    command.lineNumberColor,
                    command.opacity,
                    transform,
                    command.filter,
                )
            }
            flushTextBatch()
        }
        appendIndentGuideQuads(command, transform, scratchQuads)
        flushScratchQuads()
        appendDiagnostics(command, transform, scratchQuads)
        flushScratchQuads()
        if (command.showCaret && textFieldCaretVisible(command.node, command.caretVisibilityRevision)) {
            appendCaretQuads(command, transform, scratchQuads)
            flushScratchQuads()
        }
    }

    private fun appendIndentGuideQuads(
        command: DrawTextFieldChromeCommand,
        transform: UiMatrix4,
        quads: MutableList<UiBatchedQuad>,
    ) {
        val indentSize = command.node.indentSize ?: return
        val color = command.inlayHintColor.withOpacity(command.opacity * 0.22f).filtered(command.filter)
        val visibleLines = command.layout.visibleLineItems(command.scrollOffset.y, command.rect.height).toList()
        visibleLines.forEachIndexed { visibleIndex, (_, line) ->
            for (column in textFieldIndentGuideColumns(line.text, indentSize)) {
                val x = command.textOffset +
                        line.x +
                        UiTextLayouter.measureTextWidth(" ".repeat(column), command.fontSize, command.fontFamily) -
                        command.scrollOffset.x
                if (x < command.textOffset || x > command.rect.width) continue
                val y = line.y - command.scrollOffset.y
                if (y + line.height < 0f || y > command.rect.height) continue
                val nextLine = visibleLines.getOrNull(visibleIndex + 1)?.value
                val height = ((nextLine?.y ?: (line.y + line.height)) - line.y).coerceAtLeast(line.height)
                quads += solidQuad(
                    width = 1f,
                    height = height,
                    color = color,
                    transform = transform * UiMatrix4.translation(x, y, 0f),
                )
            }
        }
    }

    private fun appendSelectionQuads(
        command: DrawTextFieldChromeCommand,
        transform: UiMatrix4,
        quads: MutableList<UiBatchedQuad>,
    ) {
        command.carets.forEach { caretRange ->
            command.layout.selectionRects(
                caretRange.selectionStart,
                caretRange.selectionEnd,
                command.fontSize,
                command.fontFamily,
                fillLineGaps = true,
            ).forEach { rect ->
                val clipped = rect.translated(command.textOffset - command.scrollOffset.x, -command.scrollOffset.y)
                    .clipHorizontally(command.textOffset, command.rect.width)
                    ?: return@forEach
                quads += solidQuad(
                    width = clipped.width,
                    height = clipped.height,
                    color = command.selectionColor.withOpacity(command.opacity).filtered(command.filter),
                    transform = transform * UiMatrix4.translation(clipped.x, clipped.y, 0f),
                )
            }
        }
    }

    private fun appendDiagnostics(
        command: DrawTextFieldChromeCommand,
        transform: UiMatrix4,
        quads: MutableList<UiBatchedQuad>,
    ) {
        command.diagnostics.forEach { diagnostic ->
            val color = when (diagnostic.severity) {
                UiTextDiagnosticSeverity.ERROR -> command.diagnosticErrorColor
                UiTextDiagnosticSeverity.WARNING -> command.diagnosticWarningColor
                UiTextDiagnosticSeverity.INFO -> command.diagnosticInfoColor
            }
            command.layout.selectionRects(
                diagnostic.start,
                diagnostic.end.coerceAtLeast(diagnostic.start + 1),
                command.fontSize,
                command.fontFamily,
            ).forEach { rect ->
                val underline = rect.translated(command.textOffset - command.scrollOffset.x, -command.scrollOffset.y)
                    .clipHorizontally(command.textOffset, command.rect.width)
                    ?: return@forEach
                appendZigZagUnderlineQuads(
                    quads = quads,
                    rect = underline,
                    color = color.withOpacity(command.opacity).filtered(command.filter),
                    transform = transform,
                )
            }
        }
    }

    private fun appendZigZagUnderlineQuads(
        quads: MutableList<UiBatchedQuad>,
        rect: UiRect,
        color: UiColor,
        transform: UiMatrix4,
    ) {
        if (rect.width <= 1f) return
        val step = 3f
        val amplitude = 2f
        val thickness = 1.25f
        val baseY = rect.y + rect.height
        val right = rect.x + rect.width
        var startX = rect.x
        var startY = baseY
        var nextHigh = true
        while (startX < right) {
            val endX = min(startX + step, right)
            val endY = baseY + if (nextHigh) -amplitude else amplitude
            zigZagSegmentQuad(
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                thickness = thickness,
                color = color,
                transform = transform,
            )?.let(quads::add)
            startX = endX
            startY = endY
            nextHigh = !nextHigh
        }
    }

    private fun zigZagSegmentQuad(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        thickness: Float,
        color: UiColor,
        transform: UiMatrix4,
    ): UiBatchedQuad? {
        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.1f) return null
        return solidQuad(
            length,
            thickness,
            color,
            transform *
                    UiMatrix4.translation(startX, startY, 0f) *
                    UiMatrix4.rotationZ(atan2(dy, dx)) *
                    UiMatrix4.translation(0f, -thickness * 0.5f, 0f),
        )
    }

    private fun appendCaretQuads(
        command: DrawTextFieldChromeCommand,
        transform: UiMatrix4,
        quads: MutableList<UiBatchedQuad>,
    ) {
        command.carets.forEach { caretRange ->
            val caret = command.layout.caretPosition(caretRange.position, command.fontSize, command.fontFamily)
            val clipped = UiRect(
                command.textOffset + caret.x - command.scrollOffset.x,
                caret.y - command.scrollOffset.y,
                TextFieldCaretWidth,
                command.fontSize,
            ).clipHorizontally(command.textOffset, command.rect.width) ?: return@forEach
            quads += solidQuad(
                clipped.width,
                clipped.height,
                command.caretColor.withOpacity(command.opacity).filtered(command.filter),
                transform * UiMatrix4.translation(clipped.x, clipped.y, 0f),
            )
        }
    }

    private fun flushScratchQuads() {
        drawBatchedQuads(scratchQuads)
        scratchQuads.clear()
    }

    private fun flushScratchTriangles() {
        drawBatchedTriangles(scratchTriangles)
        scratchTriangles.clear()
    }

    private fun textFieldCaretVisible(node: TextFieldNode, revision: Long): Boolean {
        val now = System.currentTimeMillis()
        val state = textFieldCaretBlinkStates[node]
            ?.takeIf { it.revision == revision }
            ?: CaretBlinkState(revision, now).also { textFieldCaretBlinkStates[node] = it }
        val phase = ((now - state.startedAtMillis) % CaretBlinkPeriodMillis).toFloat() / CaretBlinkPeriodMillis.toFloat()
        return phase < 0.55f
    }

    private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect {
        return copy(x = x + deltaX, y = y + deltaY)
    }

    private fun UiRect.clipHorizontally(left: Float, right: Float): UiRect? {
        val clippedLeft = max(x, left)
        val clippedRight = min(x + width, right)
        if (clippedRight <= clippedLeft) return null
        return copy(x = clippedLeft, width = clippedRight - clippedLeft)
    }

    private fun drawCheckboxBox(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val size = min(command.rect.width, command.rect.height)
        val local = centeredSquare(command, size, transform)
        scratchTriangles.appendLocalPaint(
            size,
            size,
            3f,
            UiColor(0.16f, 0.18f, 0.22f, command.opacity),
            local,
            command.filter,
        )
        if (!command.checked) return
        drawResolvedPaint(size, size, 3f, command.activePaint, command.opacity, local, command.filter)
        val markSize = size * 0.5f
        drawResolvedPaint(
            markSize,
            markSize,
            1.5f,
            command.markPaint,
            command.opacity,
            local * UiMatrix4.translation(size * 0.25f, size * 0.25f, 0f),
            command.filter,
        )
    }

    private fun drawRadio(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val size = min(command.rect.width, command.rect.height)
        val local = centeredSquare(command, size, transform)
        scratchTriangles.appendLocalPaint(
            size,
            size,
            size * 0.5f,
            UiColor(0.16f, 0.18f, 0.22f, command.opacity),
            local,
            command.filter,
        )
        if (!command.checked) return
        drawResolvedPaint(size, size, size * 0.5f, command.activePaint, command.opacity, local, command.filter)
        val dot = size * 0.45f
        drawResolvedPaint(
            dot,
            dot,
            dot * 0.5f,
            command.markPaint,
            command.opacity,
            local * UiMatrix4.translation((size - dot) * 0.5f, (size - dot) * 0.5f, 0f),
            command.filter,
        )
    }

    private fun drawSwitch(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val height = command.rect.height
        val width = maxOf(command.rect.width, height * 1.8f)
        val local = transform * UiMatrix4.translation((command.rect.width - width) * 0.5f, 0f, 0f)
        val track = if (command.checked) command.activePaint else UiResolvedPaint.Color(UiColor(0.16f, 0.18f, 0.22f, 1f))
        drawResolvedPaint(width, height, height * 0.5f, track, command.opacity, local, command.filter)
        val knob = (height - 4f).coerceAtLeast(1f)
        val knobX = if (command.checked) width - knob - 2f else 2f
        drawResolvedPaint(
            knob,
            knob,
            knob * 0.5f,
            command.markPaint,
            command.opacity,
            local * UiMatrix4.translation(knobX, 2f, 0f),
            command.filter,
        )
    }

    private fun centeredSquare(command: DrawCheckboxCommand, size: Float, transform: UiMatrix4): UiMatrix4 {
        val x = (command.rect.width - size) * 0.5f
        val y = (command.rect.height - size) * 0.5f
        return transform * UiMatrix4.translation(x, y, 0f)
    }

    private fun drawResolvedPaint(
        width: Float,
        height: Float,
        radius: Float,
        paint: UiResolvedPaint,
        opacity: Float,
        transform: UiMatrix4,
        filter: UiFilterChain,
    ) {
        when (paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> scratchTriangles.appendLocalPaint(
                width,
                height,
                radius,
                paint.color.withOpacity(opacity),
                transform,
                filter,
            )
            is UiResolvedPaint.LinearGradient -> scratchTriangles.appendLocalGradient(
                width,
                height,
                radius,
                paint.angleDegrees,
                paint.stops,
                opacity,
                transform,
                filter,
            )
            is UiResolvedPaint.RadialGradient -> scratchTriangles.appendLocalRadialGradient(
                width,
                height,
                radius,
                paint.gradient,
                opacity,
                transform,
                filter,
            )
            is UiResolvedPaint.Image -> {
                flushScratchTriangles()
                imageDrawer(width, height, paint.source, opacity, transform, UiImageFit.STRETCH, filter, UiInsets.Zero, UiColor.White)
            }
            is UiResolvedPaint.Shader -> scratchTriangles.appendLocalPaint(
                width,
                height,
                radius,
                UiColor(0.2f, 0.2f, 0.24f, opacity),
                transform,
                filter,
            )
        }
    }

    private fun drawPlainText(
        text: String,
        localX: Float,
        localY: Float,
        fontSize: Float,
        color: UiColor,
        opacity: Float,
        transform: UiMatrix4,
        filter: UiFilterChain,
    ) {
        val mc = Minecraft.getInstance()
        RenderSystem.enableBlend()
        configureUiBlend()
        val xAxis = transform.transform(1f, 0f)
        val origin = transform.transform(0f, 0f)
        val yAxis = transform.transform(0f, 1f)
        val scaleX = sqrt((xAxis.x - origin.x) * (xAxis.x - origin.x) + (xAxis.y - origin.y) * (xAxis.y - origin.y))
        val scaleY = sqrt((yAxis.x - origin.x) * (yAxis.x - origin.x) + (yAxis.y - origin.y) * (yAxis.y - origin.y))
        val textOrigin = transform.transform(localX, localY)
        val pose = PoseStack()
        pose.translate(textOrigin.x.toDouble(), textOrigin.y.toDouble(), textOrigin.z.toDouble() - 10)
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
        mc.font.drawInBatch(
            Component.literal(text).visualOrderText,
            0f,
            0f,
            color.withOpacity(opacity).filtered(filter).argb(),
            false,
            pose.last().pose(),
            mc.renderBuffers().bufferSource(),
            DisplayMode.SEE_THROUGH,
            0,
            15728880,
        )
        markTextBatchDirty()
    }
}

internal fun textFieldIndentGuideColumns(line: String, indentSize: Int): List<Int> {
    val normalizedIndent = indentSize.coerceAtLeast(1)
    val leadingSpaces = line.takeWhile { it == ' ' }.length
    if (leadingSpaces < normalizedIndent * 2) return emptyList()
    val levels = leadingSpaces / normalizedIndent
    return (1 until levels).map { level -> level * normalizedIndent }
}

private data class CaretBlinkState(
    val revision: Long,
    val startedAtMillis: Long,
)

private const val CaretBlinkPeriodMillis = 900L
