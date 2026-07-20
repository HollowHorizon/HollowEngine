package ru.hollowhorizon.hollowengine.client.ui.shape

import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

data class UiShapeSize(
    val width: Float,
    val height: Float,
)

fun interface Shape {
    fun createPath(size: UiShapeSize): UiPath
}

class GenericShape(
    private val builder: ShapePathScope.(UiShapeSize) -> Unit,
) : Shape {
    override fun createPath(size: UiShapeSize): UiPath {
        val scope = ShapePathScope()
        scope.builder(size)
        return scope.build()
    }
}

class SvgPathShape(
    private val path: UiPath,
    private val viewBox: UiRect? = null,
) : Shape {
    private val hash = 31 * path.hashCode() + viewBox.hashCode()

    constructor(source: String, viewBox: UiRect? = null) : this(SvgPathParser.parse(source), viewBox)

    override fun createPath(size: UiShapeSize): UiPath {
        val sourceBox = viewBox ?: return path
        val scaleX = size.width / sourceBox.width.coerceAtLeast(0.0001f)
        val scaleY = size.height / sourceBox.height.coerceAtLeast(0.0001f)
        return path.transformed(
            scaleX = scaleX,
            scaleY = scaleY,
            translateX = -sourceBox.x * scaleX,
            translateY = -sourceBox.y * scaleY,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SvgPathShape) return false
        return path == other.path && viewBox == other.viewBox
    }

    override fun hashCode(): Int = hash
}

class ShapePathScope internal constructor() {
    private val builder = UiPathBuilder()

    fun moveTo(x: Float, y: Float) = builder.moveTo(x, y)

    fun lineTo(x: Float, y: Float) = builder.lineTo(x, y)

    fun horizontalLineTo(x: Float) = builder.horizontalLineTo(x)

    fun verticalLineTo(y: Float) = builder.verticalLineTo(y)

    fun curveTo(
        control1X: Float,
        control1Y: Float,
        control2X: Float,
        control2Y: Float,
        x: Float,
        y: Float,
    ) = builder.curveTo(control1X, control1Y, control2X, control2Y, x, y)

    fun smoothCurveTo(control2X: Float, control2Y: Float, x: Float, y: Float) =
        builder.smoothCurveTo(control2X, control2Y, x, y)

    fun quadraticBezierTo(controlX: Float, controlY: Float, x: Float, y: Float) =
        builder.quadraticBezierTo(controlX, controlY, x, y)

    fun quadraticCurveTo(controlX: Float, controlY: Float, x: Float, y: Float) =
        quadraticBezierTo(controlX, controlY, x, y)

    fun smoothQuadraticBezierTo(x: Float, y: Float) = builder.smoothQuadraticBezierTo(x, y)

    fun smoothQuadraticCurveTo(x: Float, y: Float) = smoothQuadraticBezierTo(x, y)

    fun ellipticalArcTo(
        radiusX: Float,
        radiusY: Float,
        xAxisRotation: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
    ) = builder.ellipticalArcTo(radiusX, radiusY, xAxisRotation, largeArc, sweep, x, y)

    fun arcTo(
        radiusX: Float,
        radiusY: Float,
        xAxisRotation: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
    ) = ellipticalArcTo(radiusX, radiusY, xAxisRotation, largeArc, sweep, x, y)

    fun close() = builder.close()

    internal fun build(): UiPath = builder.build()
}

fun svgPath(source: String, viewBox: UiRect? = null): Shape = SvgPathShape(source, viewBox)

fun path(block: ShapePathScope.() -> Unit): UiPath {
    val scope = ShapePathScope()
    scope.block()
    return scope.build()
}
