package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPath
import ru.hollowhorizon.hollowengine.client.ui.shape.UiShapeSize
import ru.hollowhorizon.hollowengine.client.ui.shape.path
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.text.caretPosition
import kotlin.math.ceil
import kotlin.math.min

internal data class ZigZagUnderlineShape(val step: Float) : Shape {
    override fun createPath(size: UiShapeSize): UiPath = path {
        moveTo(0f, size.height / 2f)
        var x = 0f
        var high = true
        while (x < size.width) {
            x = min(x + step, size.width)
            lineTo(x, if (high) 0f else size.height)
            high = !high
        }
    }
}

internal val EditableFieldZigZagShape = ZigZagUnderlineShape(EditableFieldDiagnosticStep)

internal fun UiTextDiagnosticSeverity.diagnosticUnderlineColor(): UiColor = when (this) {
    UiTextDiagnosticSeverity.ERROR -> UiColor(1f, 0.33f, 0.33f, 0.9f)
    UiTextDiagnosticSeverity.WARNING -> UiColor(1f, 0.72f, 0.26f, 0.88f)
    UiTextDiagnosticSeverity.INFO -> UiColor(0.38f, 0.66f, 1f, 0.84f)
}

internal fun bucketDiagnosticsByLine(
    lines: List<EditableFieldLine>,
    diagnostics: List<UiTextDiagnostic>,
): Array<List<UiTextDiagnostic>> {
    if (diagnostics.isEmpty()) return Array(lines.size) { emptyList() }
    val buckets = arrayOfNulls<MutableList<UiTextDiagnostic>>(lines.size)
    var lineIndex = 0
    for (diagnostic in diagnostics.sortedBy { it.start }) {
        val end = diagnostic.end.coerceAtLeast(diagnostic.start + 1)
        while (lineIndex < lines.size && lines[lineIndex].end < diagnostic.start) lineIndex++
        var index = lineIndex
        while (index < lines.size && lines[index].start < end) {
            val line = lines[index]
            val start = maxOf(diagnostic.start, line.start)
            val clipped = minOf(end, line.end)
            if (start <= clipped) {
                val bucket = buckets[index] ?: ArrayList<UiTextDiagnostic>().also { buckets[index] = it }
                bucket += diagnostic.copy(start = start - line.start, end = clipped - line.start)
            }
            index++
        }
    }
    return Array(lines.size) { buckets[it] ?: emptyList() }
}

@Composable
internal fun EditableFieldRowDiagnostics(
    line: EditableFieldLine,
    lineLayout: UiTextLayout?,
    rowDiagnostics: List<UiTextDiagnostic>,
    top: Float,
    fontSize: Float,
    fontFamily: String?,
    contentWidth: Float,
) {
    rowDiagnostics.forEachIndexed { diagnosticIndex, diagnostic ->
        val localStart = diagnostic.start.coerceIn(0, line.text.length)
        val localEnd = diagnostic.end.coerceIn(localStart, line.text.length)
        val rects = selectionRectsForRow(
            line, lineLayout, localStart, localEnd,
            crossesNewline = false,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fullWidth = contentWidth,
        ).ifEmpty {
            val caret = lineLayout?.caretPosition(localStart, fontSize, fontFamily)
            val x = caret?.x ?: UiTextLayouter.measureTextWidth(line.text.take(localStart), fontSize, fontFamily)
            listOf(UiRect(x, caret?.y ?: 0f, 0f, fontSize))
        }
        val color = diagnostic.severity.diagnosticUnderlineColor()
        rects.forEachIndexed { rectIndex, rect ->
            val width = (ceil(rect.width / EditableFieldDiagnosticStep) * EditableFieldDiagnosticStep)
                .coerceAtLeast(EditableFieldDiagnosticStep * 2f)
            key("diag", diagnosticIndex, rectIndex) {
                Box(
                    modifier = Modifier
                        .position(rect.x.px, (top + rect.y + rect.height - EditableFieldDiagnosticAmplitude).px)
                        .size(width.px, (EditableFieldDiagnosticAmplitude * 2f).px)
                        .shape(
                            EditableFieldZigZagShape,
                            fill = UiPaint.None,
                            stroke = UiPaint.Color(color),
                            strokeWidth = EditableFieldDiagnosticThickness.px,
                        ),
                )
            }
        }
    }
}

internal data class EditableFieldDiagnosticTooltip(
    val message: String,
    val severity: UiTextDiagnosticSeverity,
    val x: Float,
    val y: Float,
    val width: Float,
)

internal fun editableFieldDiagnosticTooltipAt(
    diagnostics: List<UiTextDiagnostic>,
    layout: EditableFieldLayout,
    pointerX: Float,
    pointerY: Float,
    scrollX: Float,
    scrollY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    contentOffsetX: Float = 0f,
    originX: Float = 0f,
    originY: Float = 0f,
): EditableFieldDiagnosticTooltip? {
    if (diagnostics.isEmpty()) return null
    val contentX = pointerX - contentOffsetX + scrollX
    val contentY = pointerY + scrollY
    if (contentX < 0f || contentY < 0f || contentY > layout.height) return null
    val index = layout.offsetAt(contentX, contentY)
    val diagnostic = diagnostics.firstOrNull { candidate ->
        index in candidate.start until candidate.end.coerceAtLeast(candidate.start + 1)
    } ?: return null
    val message = diagnostic.message.take(220)
    val maxWidth = (viewportWidth - 12f).coerceIn(140f, 420f)
    val measured = UiTextLayouter.measure(
        text = message,
        availableWidth = maxWidth - DiagnosticTooltipHorizontalPadding,
        knownWidth = null,
        wrap = true,
        fontSize = DiagnosticTooltipFontSize,
        fontFamily = layout.fontFamily,
    )
    val width = (measured.width + DiagnosticTooltipHorizontalPadding).coerceIn(140f, maxWidth)
    val height = (measured.height + DiagnosticTooltipVerticalPadding)
        .coerceIn(DiagnosticTooltipMinHeight, DiagnosticTooltipMaxHeight)
    val x = (originX + pointerX + 12f).coerceIn(4f, (viewportWidth - width - 4f).coerceAtLeast(4f))
    val y = (originY + pointerY + 16f).coerceIn(4f, (viewportHeight - height - 4f).coerceAtLeast(4f))
    return EditableFieldDiagnosticTooltip(
        message = message,
        severity = diagnostic.severity,
        x = x,
        y = y,
        width = width,
    )
}

/**
 * The message of the diagnostic under the pointer.
 */
@Composable
internal fun EditableFieldDiagnosticTooltipOverlay(tooltip: EditableFieldDiagnosticTooltip, visible: Boolean = true) {
    Popup(
        anchorBounds = UiRect(tooltip.x, tooltip.y, 0f, 0f),
        alignment = UiPopupAlignment(anchorVertical = UiAlign.START),
        layer = 31,
        visible = visible,
        tags = listOf(
            "editable-text-field-diagnostic-tooltip",
            "ide-diagnostic-tooltip",
            tooltip.severity.name.lowercase(),
        ),
        modifier = Modifier.size(tooltip.width.px, UiLength.Auto).inputTransparent(),
        dismissOnOutside = false,
    ) {
        Text(
            tooltip.message,
            tags = listOf("ide-diagnostic-tooltip-message"),
            modifier = Modifier.size(UiLength.Fill, UiLength.Auto)
                .whitespace(UiWhitespace.COLLAPSE)
                .textWrap(true)
                .fontSize(DiagnosticTooltipFontSize),
        )
    }
}

internal const val EditableFieldDiagnosticStep = 3f
internal const val EditableFieldDiagnosticAmplitude = 2f
internal const val EditableFieldDiagnosticThickness = 1.25f
private const val DiagnosticTooltipFontSize = 11f
private const val DiagnosticTooltipHorizontalPadding = 18f
private const val DiagnosticTooltipVerticalPadding = 10f
private const val DiagnosticTooltipMinHeight = 24f
private const val DiagnosticTooltipMaxHeight = 128f
