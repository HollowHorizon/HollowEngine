package ru.hollowhorizon.hollowengine.client.ui.render

import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.resolve
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.min

internal class UiAnalyticRectBatch {
    private val vertices = UiFloatArrayBuilder()
    private val records = UiFloatArrayBuilder()
    private val paints = UiFloatArrayBuilder()
    private val stops = UiFloatArrayBuilder()
    private val paintEncoder = UiPaintBufferEncoder(paints, stops)

    val isEmpty: Boolean get() = vertices.size == 0
    val vertexCount: Int get() = vertices.size / VertexStride
    val vertexFloatCount: Int get() = vertices.size
    val recordFloatCount: Int get() = records.size
    val paintFloatCount: Int get() = paints.size
    val stopFloatCount: Int get() = stops.size

    fun canAppend(command: DrawBoxCommand): Boolean {
        if (command.renderToFramebuffer) return false
        if (command.paint != UiResolvedPaint.None && !command.paint.isBufferPaint()) return false
        val borderWidth = uniformBorderWidth(command) ?: return false
        return command.border.radius > 0f || borderWidth > 0f
    }

    fun append(command: DrawBoxCommand, transform: UiMatrix4) {
        val width = command.rect.width
        val height = command.rect.height
        if (width <= 0f || height <= 0f || command.opacity <= 0f) return
        val borderWidth = checkNotNull(uniformBorderWidth(command)).coerceIn(0f, min(width, height) * 0.5f)
        if (command.paint == UiResolvedPaint.None && (borderWidth <= 0f || command.border.color.alpha <= 0f)) return
        val paintIndex = if (command.paint == UiResolvedPaint.None) {
            NoPaint
        } else {
            paintEncoder.append(command.paint, command.opacity, command.filter, width, height)
        }
        val borderColor = if (borderWidth > 0f) {
            command.border.color.withOpacity(command.opacity).filtered(command.filter)
        } else {
            UiColor.Transparent
        }
        val radius = command.border.radius.coerceIn(0f, min(width, height) * 0.5f)
        val recordIndex = records.size / RecordStride
        records.add(width, height, radius, borderWidth)
        records.add(paintIndex.toFloat(), 0f, 0f, 0f)
        records.add(borderColor.red, borderColor.green, borderColor.blue, borderColor.alpha)
        appendVertex(0f, 0f, transform, recordIndex)
        appendVertex(0f, height, transform, recordIndex)
        appendVertex(width, height, transform, recordIndex)
        appendVertex(0f, 0f, transform, recordIndex)
        appendVertex(width, height, transform, recordIndex)
        appendVertex(width, 0f, transform, recordIndex)
    }

    fun clear() {
        vertices.clear()
        records.clear()
        paints.clear()
        stops.clear()
    }

    fun writeVertices(destination: FloatBuffer) = vertices.writeTo(destination)
    fun writeRecords(destination: FloatBuffer) = records.writeTo(destination)
    fun writePaints(destination: FloatBuffer) = paints.writeTo(destination)
    fun writeStops(destination: FloatBuffer) = stops.writeTo(destination)

    private fun appendVertex(x: Float, y: Float, transform: UiMatrix4, recordIndex: Int) {
        val point = transform.transform(x, y)
        vertices.add(point.x, point.y, point.z, x, y, recordIndex.toFloat())
    }

    private fun uniformBorderWidth(command: DrawBoxCommand): Float? {
        val width = command.rect.width
        val height = command.rect.height
        val left = command.border.width.left.resolve(width)
        val top = command.border.width.top.resolve(height)
        val right = command.border.width.right.resolve(width)
        val bottom = command.border.width.bottom.resolve(height)
        return left.takeIf {
            abs(it - top) <= BorderEpsilon &&
                    abs(it - right) <= BorderEpsilon &&
                    abs(it - bottom) <= BorderEpsilon
        }
    }

    companion object {
        const val VertexStride = 6
        const val RecordStride = 12
        const val NoPaint = -1
        private const val BorderEpsilon = 0.001f
    }
}
