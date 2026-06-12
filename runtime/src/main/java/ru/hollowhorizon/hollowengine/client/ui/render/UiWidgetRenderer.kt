package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class UiWidgetRenderer(
    private val imageDrawer: (Float, Float, String, Float, UiMatrix4, UiImageFit, UiFilterChain, UiInsets, UiColor) -> Unit,
) {
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
            drawLocalBorder(
                command.thumbWidth,
                command.thumbHeight,
                command.thumbBorder.radius,
                borderWidth,
                command.thumbBorder.color.withOpacity(command.opacity),
                thumbTransform,
            )
        }
    }

    fun drawCheckbox(command: DrawCheckboxCommand, transform: UiMatrix4) {
        when (command.variant) {
            UiCheckboxVariant.SWITCH -> drawSwitch(command, transform)
            UiCheckboxVariant.RADIO -> drawRadio(command, transform)
            UiCheckboxVariant.CHECKBOX -> drawCheckboxBox(command, transform)
        }
    }

    fun drawTextFieldChrome(command: DrawTextFieldChromeCommand, transform: UiMatrix4) {
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
                drawLocalPaint(
                    clipped.width,
                    clipped.height,
                    0f,
                    command.selectionColor.withOpacity(command.opacity),
                    transform * UiMatrix4.translation(clipped.x, clipped.y, 0f),
                    command.filter,
                )
            }
        }
        if (command.showLineNumbers) {
            command.layout.lines.forEachIndexed { index, line ->
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
        }
        drawDiagnostics(command, transform)
        if (command.showInlayHints) {
            drawInlayHintFrames(command, transform)
        }
        drawCompletionPopup(command, transform)
        if (command.showCaret) {
            if (!textFieldCaretVisible()) return
            command.carets.forEach { caretRange ->
                val caret = command.layout.caretPosition(caretRange.position, command.fontSize, command.fontFamily)
                val clipped = UiRect(
                    command.textOffset + caret.x - command.scrollOffset.x,
                    caret.y - command.scrollOffset.y,
                    TextFieldCaretWidth,
                    command.fontSize,
                ).clipHorizontally(command.textOffset, command.rect.width) ?: return@forEach
                drawLocalPaint(
                    clipped.width,
                    clipped.height,
                    0f,
                    command.caretColor.withOpacity(command.opacity),
                    transform * UiMatrix4.translation(clipped.x, clipped.y, 0f),
                    command.filter,
                )
            }
        }
    }

    private fun drawDiagnostics(command: DrawTextFieldChromeCommand, transform: UiMatrix4) {
        command.diagnostics.forEach { diagnostic ->
            val color = when (diagnostic.severity) {
                UiTextDiagnosticSeverity.ERROR -> command.diagnosticErrorColor
                UiTextDiagnosticSeverity.WARNING -> command.diagnosticWarningColor
                UiTextDiagnosticSeverity.INFO -> command.diagnosticInfoColor
            }
            command.layout.selectionRects(
                diagnostic.start,
                diagnostic.end,
                command.fontSize,
            command.fontFamily,
        ).forEach { rect ->
                val underline = rect.translated(command.textOffset - command.scrollOffset.x, -command.scrollOffset.y)
                    .clipHorizontally(command.textOffset, command.rect.width)
                    ?: return@forEach
                drawZigZagUnderline(underline, color, command, transform)
            }
        }
    }

    private fun drawZigZagUnderline(
        rect: UiRect,
        color: UiColor,
        command: DrawTextFieldChromeCommand,
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
            drawZigZagSegment(
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                thickness = thickness,
                color = color.withOpacity(command.opacity),
                transform = transform,
                filter = command.filter,
            )
            startX = endX
            startY = endY
            nextHigh = !nextHigh
        }
    }

    private fun drawZigZagSegment(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        thickness: Float,
        color: UiColor,
        transform: UiMatrix4,
        filter: UiFilterChain,
    ) {
        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.1f) return
        drawLocalPaint(
            length,
            thickness,
            thickness * 0.5f,
            color,
            transform *
                    UiMatrix4.translation(startX, startY, 0f) *
                    UiMatrix4.rotationZ(atan2(dy, dx)) *
                    UiMatrix4.translation(0f, -thickness * 0.5f, 0f),
            filter,
        )
    }

    private fun drawInlayHintFrames(command: DrawTextFieldChromeCommand, transform: UiMatrix4) {
        command.layout.lines.forEach { line ->
            line.fragments.filterIsInstance<UiInlayTextRun>().forEach { fragment ->
                val frame = UiRect(
                    command.textOffset + line.x + fragment.x - command.scrollOffset.x + InlayHintVisualOffsetX,
                    line.y + fragment.y - command.scrollOffset.y - 1f,
                    fragment.width,
                    fragment.height + 2f,
                ).clipHorizontally(command.textOffset, command.rect.width) ?: return@forEach
                drawLocalBorder(
                    frame.width,
                    frame.height,
                    3f,
                    1f,
                    command.inlayHintColor.withOpacity(command.opacity * 0.55f),
                    transform * UiMatrix4.translation(frame.x, frame.y, 0f),
                )
            }
        }
    }

    private fun drawCompletionPopup(command: DrawTextFieldChromeCommand, transform: UiMatrix4) {
        val items = command.completionItems.take(6)
        if (items.isEmpty()) return
        val rowHeight = (command.fontSize + 5f).coerceAtLeast(12f)
        val popupHeight = rowHeight * items.size + 6f
        val labelWidth = items.maxOfOrNull { item ->
            val detail = if (item.detail.isBlank()) "" else "  ${item.detail}"
            (item.label.length + detail.length) * command.fontSize * 0.56f
        } ?: 0f
        val popupWidth = (labelWidth + 18f).coerceIn(90f, max(90f, command.rect.width - 8f))
        val caret = command.layout.caretPosition(command.completionAnchor, command.fontSize, command.fontFamily)
        val preferredX = command.textOffset + caret.x - command.scrollOffset.x
        val popupX = preferredX.coerceIn(4f, (command.rect.width - popupWidth - 4f).coerceAtLeast(4f))
        val belowY = caret.y + command.fontSize - command.scrollOffset.y + 4f
        val aboveY = caret.y - command.scrollOffset.y - popupHeight - 4f
        val popupY = if (belowY + popupHeight <= command.rect.height) {
            belowY
        } else {
            aboveY.coerceAtLeast(4f)
        }
        val popupTransform = transform * UiMatrix4.translation(popupX, popupY, 30f)
        drawLocalPaint(popupWidth, popupHeight, 3f, UiColor(0.08f, 0.09f, 0.11f, command.opacity * 0.96f), popupTransform, command.filter)
        drawLocalBorder(popupWidth, popupHeight, 3f, 1f, UiColor(0.36f, 0.42f, 0.5f, command.opacity * 0.75f), popupTransform)
        items.forEachIndexed { index, item ->
            val rowY = 3f + index * rowHeight
            if (index == command.completionSelectedIndex.coerceIn(0, items.lastIndex)) {
                drawLocalPaint(
                    popupWidth - 4f,
                    rowHeight,
                    2f,
                    UiColor(0.22f, 0.32f, 0.46f, command.opacity * 0.7f),
                    popupTransform * UiMatrix4.translation(2f, rowY, 0f),
                    command.filter,
                )
            }
            drawPlainText(
                item.label,
                8f,
                rowY + 2f,
                command.fontSize,
                UiColor(0.9f, 0.94f, 1f, 1f),
                command.opacity,
                popupTransform,
                command.filter,
            )
            if (item.detail.isNotBlank()) {
                drawPlainText(
                    item.detail,
                    (item.label.length * command.fontSize * 0.56f + 16f).coerceAtMost(popupWidth - 42f),
                    rowY + 2f,
                    command.fontSize,
                    command.inlayHintColor,
                    command.opacity,
                    popupTransform,
                    command.filter,
                )
            }
        }
    }

    private fun textFieldCaretVisible(): Boolean {
        val phase = (System.currentTimeMillis() % 900L).toFloat() / 900f
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
        drawLocalPaint(size, size, 3f, UiColor(0.16f, 0.18f, 0.22f, command.opacity), local, command.filter)
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
        drawLocalPaint(size, size, size * 0.5f, UiColor(0.16f, 0.18f, 0.22f, command.opacity), local, command.filter)
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
            is UiResolvedPaint.Color -> drawLocalPaint(width, height, radius, paint.color.withOpacity(opacity), transform, filter)
            is UiResolvedPaint.LinearGradient -> drawLocalGradient(width, height, radius, paint.angleDegrees, paint.stops, opacity, transform, filter)
            is UiResolvedPaint.Image -> imageDrawer(width, height, paint.source, opacity, transform, UiImageFit.STRETCH, filter, UiInsets.Zero, UiColor.White)
            is UiResolvedPaint.Shader -> drawLocalPaint(width, height, radius, UiColor(0.2f, 0.2f, 0.24f, opacity), transform, filter)
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
        mc.renderBuffers().bufferSource().endBatch()
    }
}
