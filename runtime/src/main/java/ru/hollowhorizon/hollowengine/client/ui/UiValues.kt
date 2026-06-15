package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.UiLength.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class UiState(val selectorName: String) {
    HOVER("hover"),
    ACTIVE("active"),
    FOCUS("focus"),
    SELECTED("selected"),
    DISABLED("disabled"),
    DRAGGING("dragging"),
    CLOSING("closing");

    companion object {
        fun fromSelector(name: String): UiState? = entries.firstOrNull { it.selectorName == name }
    }
}

enum class UiNodeType(val typeName: String) {
    BOX("box"),
    TEXT("text"),
    IMAGE("image"),
    ITEM("item"),
    ENTITY("entity"),
    CANVAS("canvas"),
    SLIDER("slider"),
    CHECKBOX("checkbox"),
    TEXT_FIELD("text-field"),
    POPUP("popup");
}

sealed interface UiLayout {
    data object Column : UiLayout
    data object Row : UiLayout
    data object LazyColumn : UiLayout
    data object LazyRow : UiLayout
    data class Box(val mode: UiBoxMode = UiBoxMode.FREE) : UiLayout
    data class Custom(val measurePolicy: UiMeasurePolicy) : UiLayout
}

enum class UiBoxMode {
    FREE,
    STACK
}

data class UiConstraints(
    val minWidth: Float = 0f,
    val maxWidth: Float = Float.POSITIVE_INFINITY,
    val minHeight: Float = 0f,
    val maxHeight: Float = Float.POSITIVE_INFINITY,
) {
    init {
        require(minWidth >= 0f && minHeight >= 0f) { "Constraint minimums must be non-negative" }
        require(maxWidth >= minWidth) { "maxWidth must be greater than or equal to minWidth" }
        require(maxHeight >= minHeight) { "maxHeight must be greater than or equal to minHeight" }
    }

    fun constrainWidth(width: Float): Float = width.coerceIn(minWidth, maxWidth)

    fun constrainHeight(height: Float): Float = height.coerceIn(minHeight, maxHeight)

    companion object {
        fun fixed(width: Float, height: Float): UiConstraints {
            return UiConstraints(width, width, height, height)
        }
    }
}

fun interface UiMeasurePolicy {
    fun UiMeasureScope.measure(measurables: List<UiMeasurable>, constraints: UiConstraints): UiMeasureResult
}

interface UiMeasurable {
    val node: UiNode
    fun measure(constraints: UiConstraints): UiPlaceable
}

data class UiPlaceable(
    val width: Float,
    val height: Float,
    internal val node: UiNode,
)

data class UiMeasureResult(
    val width: Float,
    val height: Float,
    internal val placements: List<UiPlacement>,
)

data class UiPlacement(
    val placeable: UiPlaceable,
    val x: Float,
    val y: Float,
)

class UiMeasureScope {
    fun layout(width: Float, height: Float, block: UiPlacementScope.() -> Unit = {}): UiMeasureResult {
        val scope = UiPlacementScope()
        scope.block()
        return UiMeasureResult(width.coerceAtLeast(0f), height.coerceAtLeast(0f), scope.placements)
    }
}

class UiPlacementScope internal constructor() {
    internal val placements = mutableListOf<UiPlacement>()

    fun UiPlaceable.place(x: Float, y: Float) {
        placements += UiPlacement(this, x, y)
    }

    fun UiPlaceable.place(x: Int, y: Int) {
        place(x.toFloat(), y.toFloat())
    }
}

sealed interface UiPopupAnchor {
    data object Parent : UiPopupAnchor
    data class Node(val id: String) : UiPopupAnchor
    data class Cursor(val x: Float = Float.NaN, val y: Float = Float.NaN) : UiPopupAnchor
}

data class UiPopupAlignment(
    val anchorHorizontal: UiAlign = UiAlign.START,
    val anchorVertical: UiAlign = UiAlign.END,
    val popupHorizontal: UiAlign = UiAlign.START,
    val popupVertical: UiAlign = UiAlign.START,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        val BelowStart = UiPopupAlignment()
        val Cursor = UiPopupAlignment(anchorVertical = UiAlign.START, offsetX = 8f, offsetY = 8f)
    }
}

enum class UiAlign {
    AUTO,
    START,
    CENTER,
    END,
    STRETCH,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY
}

enum class UiTextAlign {
    LEFT,
    RIGHT,
    CENTER,
    JUSTIFY
}

enum class UiCursorShape {
    DEFAULT,
    HAND,
    MOVE,
    TEXT,
    RESIZE_HORIZONTAL,
    RESIZE_VERTICAL,
    RESIZE_NESW,
    RESIZE_NWSE
}

sealed interface UiLength {
    data object Auto : UiLength
    data object Fill : UiLength
    data class Px(val value: Float) : UiLength
    data class Percent(val value: Float) : UiLength
    data class Addition(val first: UiLength, val second: UiLength) : UiLength
    data class Substraction(val first: UiLength, val second: UiLength) : UiLength

    fun resolve(reference: Float, autoValue: Float = 0f): Float = when (this) {
        Auto -> autoValue
        Fill -> reference
        is Px -> value
        is Percent -> reference * value
        is Addition -> first.resolve(reference, autoValue) + second.resolve(reference, autoValue)
        is Substraction -> first.resolve(reference, autoValue) - second.resolve(reference, autoValue)
    }
}

operator fun UiLength.plus(other: UiLength): UiLength = Addition(this, other)
operator fun UiLength.minus(other: UiLength): UiLength = Substraction(this, other)

val Number.px: Px get() = Px(toFloat())
val Number.percent: Percent get() = Percent(toFloat() / 100f)

data class UiSize(
    val width: UiLength = Auto,
    val height: UiLength = Auto,
)

data class UiInsets(
    val left: UiLength = 0.px,
    val top: UiLength = 0.px,
    val right: UiLength = 0.px,
    val bottom: UiLength = 0.px,
) {
    companion object {
        val Zero = UiInsets()

        fun all(value: UiLength) = UiInsets(value, value, value, value)

        fun hv(horizontal: UiLength, vertical: UiLength) = UiInsets(horizontal, vertical, horizontal, vertical)
    }
}

data class UiVec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

data class UiPosition(
    val x: UiLength = 0.px,
    val y: UiLength = 0.px,
    val z: Float = 0f,
) {
    fun resolve(parentWidth: Float, parentHeight: Float): UiVec3 = UiVec3(
        x = x.resolve(parentWidth),
        y = y.resolve(parentHeight),
        z = z,
    )
}

data class UiTransformPivot(
    val x: UiLength = 50.percent,
    val y: UiLength = 50.percent,
    val z: UiLength = 0.px,
) {
    fun resolve(width: Float, height: Float, depth: Float = 0f): UiVec3 = UiVec3(
        x = x.resolve(width),
        y = y.resolve(height),
        z = z.resolve(depth),
    )

    companion object {
        val Center = UiTransformPivot()
        val TopLeft = UiTransformPivot(0.px, 0.px, 0.px)
        val TopCenter = UiTransformPivot(50.percent, 0.px, 0.px)
        val TopRight = UiTransformPivot(100.percent, 0.px, 0.px)
        val CenterLeft = UiTransformPivot(0.px, 50.percent, 0.px)
        val CenterRight = UiTransformPivot(100.percent, 50.percent, 0.px)
        val BottomLeft = UiTransformPivot(0.px, 100.percent, 0.px)
        val BottomCenter = UiTransformPivot(50.percent, 100.percent, 0.px)
        val BottomRight = UiTransformPivot(100.percent, 100.percent, 0.px)
    }
}

data class UiColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    fun interpolate(to: UiColor, progress: Float) = UiColor(
        red = red + (to.red - red) * progress,
        green = green + (to.green - green) * progress,
        blue = blue + (to.blue - blue) * progress,
        alpha = alpha + (to.alpha - alpha) * progress,
    )

    companion object {
        val Transparent = UiColor(0f, 0f, 0f, 0f)
        val White = UiColor(1f, 1f, 1f, 1f)
        val Black = UiColor(0f, 0f, 0f, 1f)
    }
}

data class UiBorder(
    val width: UiInsets = UiInsets.Zero,
    val color: UiColor = UiColor.Transparent,
    val radius: Float = 0f,
)

data class UiTransform(
    val translate: UiVec3 = UiVec3(),
    val rotate: UiVec3 = UiVec3(),
    val scale: UiVec3 = UiVec3(1f, 1f, 1f),
    val pivot: UiTransformPivot = UiTransformPivot.Center,
    val perspective: Float = 0f,
) {
    val needsFramebuffer: Boolean get() = !rotate.x.isAlmostZero() || !rotate.y.isAlmostZero()

    private fun Float.isAlmostZero(epsilon: Float = 0.0001f): Boolean =
        kotlin.math.abs(this) <= epsilon

    fun matrix(pivotPoint: UiVec3 = pivot.resolve(0f, 0f)): UiMatrix4 {
        var result = UiMatrix4.identity()
        result *= UiMatrix4.translation(translate.x, translate.y, translate.z)
        result *= UiMatrix4.translation(pivotPoint.x, pivotPoint.y, pivotPoint.z)
        if (perspective != 0f) result *= UiMatrix4.perspective(perspective)
        result *= UiMatrix4.rotationX(rotate.x.degreesToRadians())
        result *= UiMatrix4.rotationY(rotate.y.degreesToRadians())
        result *= UiMatrix4.rotationZ(rotate.z.degreesToRadians())
        result *= UiMatrix4.scale(scale.x, scale.y, scale.z)
        result *= UiMatrix4.translation(-pivotPoint.x, -pivotPoint.y, -pivotPoint.z)
        return result
    }
}

private fun Float.degreesToRadians(): Float = this * PI.toFloat() / 180f

class UiMatrix4(private val values: FloatArray) {
    operator fun times(other: UiMatrix4): UiMatrix4 {
        val result = FloatArray(16)
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                var sum = 0f
                for (i in 0 until 4) {
                    sum += values[row * 4 + i] * other.values[i * 4 + column]
                }
                result[row * 4 + column] = sum
            }
        }
        return UiMatrix4(result)
    }

    fun transform(x: Float, y: Float, z: Float = 0f): UiVec3 {
        val tx = values[0] * x + values[1] * y + values[2] * z + values[3]
        val ty = values[4] * x + values[5] * y + values[6] * z + values[7]
        val tz = values[8] * x + values[9] * y + values[10] * z + values[11]
        val tw = values[12] * x + values[13] * y + values[14] * z + values[15]
        if (tw == 0f || tw == 1f) return UiVec3(tx, ty, tz)
        return UiVec3(tx / tw, ty / tw, tz / tw)
    }

    fun inverse(): UiMatrix4? {
        val m = values
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var determinant = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (determinant == 0f) return null
        determinant = 1f / determinant
        for (i in inv.indices) inv[i] *= determinant
        return UiMatrix4(inv)
    }

    companion object {
        fun identity() = UiMatrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            )
        )

        fun translation(x: Float, y: Float, z: Float) = UiMatrix4(
            floatArrayOf(
                1f, 0f, 0f, x,
                0f, 1f, 0f, y,
                0f, 0f, 1f, z,
                0f, 0f, 0f, 1f,
            )
        )

        fun scale(x: Float, y: Float, z: Float) = UiMatrix4(
            floatArrayOf(
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, z, 0f,
                0f, 0f, 0f, 1f,
            )
        )

        fun rotationX(radians: Float): UiMatrix4 {
            val c = cos(radians)
            val s = sin(radians)
            return UiMatrix4(floatArrayOf(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f))
        }

        fun rotationY(radians: Float): UiMatrix4 {
            val c = cos(radians)
            val s = sin(radians)
            return UiMatrix4(floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f, 0f, 0f, 0f, 1f))
        }

        fun rotationZ(radians: Float): UiMatrix4 {
            val c = cos(radians)
            val s = sin(radians)
            return UiMatrix4(floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        }

        fun perspective(distance: Float) = UiMatrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, -1f / distance, 1f,
            )
        )
    }
}
