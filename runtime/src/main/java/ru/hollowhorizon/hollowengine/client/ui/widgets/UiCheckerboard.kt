package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.drawBehind
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import kotlin.math.ceil
import kotlin.math.max

fun Modifier.checkerboard(
    cellSize: Float = 8f,
    light: UiColor = CheckerLight,
    dark: UiColor = CheckerDark,
): Modifier = drawBehind(CheckerboardKey(cellSize, light, dark)) {
    if (size.width <= 0f || size.height <= 0f) return@drawBehind
    val requestedCell = cellSize.coerceAtLeast(1f)
    val boundedCell = max(requestedCell, max(size.width, size.height) / MaxCellsPerAxis)
    val columns = ceil(size.width / boundedCell).toInt()
    val rows = ceil(size.height / boundedCell).toInt()
    drawRect(UiPaint.Color(light))
    for (row in 0 until rows) {
        for (column in (row and 1) until columns step 2) {
            drawRect(
                UiRect(
                    x = column * boundedCell,
                    y = row * boundedCell,
                    width = minOf(boundedCell, size.width - column * boundedCell),
                    height = minOf(boundedCell, size.height - row * boundedCell),
                ),
                UiPaint.Color(dark),
            )
        }
    }
}

private data class CheckerboardKey(
    val cellSize: Float,
    val light: UiColor,
    val dark: UiColor,
)

private const val MaxCellsPerAxis = 128f
private val CheckerLight = UiColor(0.72f, 0.74f, 0.78f, 1f)
private val CheckerDark = UiColor(0.48f, 0.5f, 0.55f, 1f)
