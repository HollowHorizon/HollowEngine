package ru.hollowhorizon.hollowengine.client.ui

import java.awt.Shape as AwtShape
import java.awt.BasicStroke
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.tan
import kotlin.math.sin

data class UiSvgPathElement(
    val path: UiPath,
    val style: UiSvgStyle = UiSvgStyle.Default,
    val id: String? = null,
)

enum class UiSvgStrokeLineCap {
    BUTT,
    ROUND,
    SQUARE,
}

enum class UiSvgStrokeLineJoin {
    MITER,
    ROUND,
    BEVEL,
}

enum class UiSvgTextAnchor {
    START,
    MIDDLE,
    END,
}

data class UiSvgStyle(
    val fill: UiColor? = UiColor.Black,
    val stroke: UiColor? = null,
    val strokeWidth: Float = 1f,
    val strokeLineCap: UiSvgStrokeLineCap = UiSvgStrokeLineCap.BUTT,
    val strokeLineJoin: UiSvgStrokeLineJoin = UiSvgStrokeLineJoin.MITER,
    val color: UiColor = UiColor.Black,
    val opacity: Float = 1f,
    val fillOpacity: Float = 1f,
    val strokeOpacity: Float = 1f,
    val fontFamily: String = "Serif",
    val fontSize: Float = 16f,
    val textAnchor: UiSvgTextAnchor = UiSvgTextAnchor.START,
    val display: Boolean = true,
    val visibility: Boolean = true,
    val clipPath: String? = null,
    val mask: String? = null,
    val filter: String? = null,
) {
    fun fillColor(): UiColor? = fill?.withAlphaMultiplier(opacity * fillOpacity)

    fun strokeColor(): UiColor? = stroke?.withAlphaMultiplier(opacity * strokeOpacity)

    companion object {
        val Default = UiSvgStyle()
    }
}

data class UiSvgTransform(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
) {
    fun transform(point: UiPathPoint): UiPathPoint {
        return UiPathPoint(
            x = a * point.x + c * point.y + e,
            y = b * point.x + d * point.y + f,
        )
    }

    operator fun times(other: UiSvgTransform): UiSvgTransform {
        return UiSvgTransform(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            e = a * other.e + c * other.f + e,
            f = b * other.e + d * other.f + f,
        )
    }

    fun isIdentity(): Boolean = this == Identity

    fun toAwt(): AffineTransform = AffineTransform(
        a.toDouble(),
        b.toDouble(),
        c.toDouble(),
        d.toDouble(),
        e.toDouble(),
        f.toDouble(),
    )

    companion object {
        val Identity = UiSvgTransform(1f, 0f, 0f, 1f, 0f, 0f)

        fun translation(x: Float, y: Float) = UiSvgTransform(1f, 0f, 0f, 1f, x, y)

        fun scale(x: Float, y: Float) = UiSvgTransform(x, 0f, 0f, y, 0f, 0f)

        fun rotation(degrees: Float): UiSvgTransform {
            val radians = degrees / 180f * PI.toFloat()
            val cos = cos(radians)
            val sin = sin(radians)
            return UiSvgTransform(cos, sin, -sin, cos, 0f, 0f)
        }

        fun rotation(degrees: Float, centerX: Float, centerY: Float): UiSvgTransform {
            return translation(centerX, centerY) * rotation(degrees) * translation(-centerX, -centerY)
        }

        fun skewX(degrees: Float): UiSvgTransform {
            val radians = degrees / 180f * PI.toFloat()
            return UiSvgTransform(1f, 0f, tan(radians), 1f, 0f, 0f)
        }

        fun skewY(degrees: Float): UiSvgTransform {
            val radians = degrees / 180f * PI.toFloat()
            return UiSvgTransform(1f, tan(radians), 0f, 1f, 0f, 0f)
        }
    }
}

internal fun UiPath.transformed(transform: UiSvgTransform): UiPath {
    if (transform.isIdentity()) return this
    if (commands.any { it is UiPathCommand.ArcTo }) return flatten().toPath(transform)
    fun UiPathPoint.t() = transform.transform(this)
    return UiPath(
        commands.map { command ->
            when (command) {
                is UiPathCommand.MoveTo -> command.copy(target = command.target.t())
                is UiPathCommand.LineTo -> command.copy(target = command.target.t())
                is UiPathCommand.CubicTo -> command.copy(
                    control1 = command.control1.t(),
                    control2 = command.control2.t(),
                    target = command.target.t(),
                )
                is UiPathCommand.QuadraticTo -> command.copy(
                    control = command.control.t(),
                    target = command.target.t(),
                )
                is UiPathCommand.ArcTo -> command
                UiPathCommand.Close -> UiPathCommand.Close
            }
        },
    )
}

internal fun UiPathGeometry.toPath(transform: UiSvgTransform = UiSvgTransform.Identity): UiPath {
    val builder = UiPathBuilder()
    contours.forEach { contour ->
        val points = contour.points
        if (points.isEmpty()) return@forEach
        val first = transform.transform(points.first())
        builder.moveTo(first.x, first.y)
        points.drop(1).forEach { point ->
            val transformed = transform.transform(point)
            builder.lineTo(transformed.x, transformed.y)
        }
        if (contour.closed) builder.close()
    }
    return builder.build()
}

internal fun combineSvgPaths(paths: List<UiPath>): UiPath {
    return UiPath(paths.flatMap { it.commands })
}

internal fun UiPath.withSvgStrokeGeometry(style: UiSvgStyle): UiPath {
    val strokeWidth = style.strokeWidth
    if (style.strokeColor() == null || strokeWidth <= 0f || isEmpty()) return this
    val stroke = BasicStroke(
        strokeWidth,
        style.strokeLineCap.toAwtStrokeCap(),
        style.strokeLineJoin.toAwtStrokeJoin(),
    ).createStrokedShape(toAwtPath()).toUiPath(flatness = 0.5)
    return combineSvgPaths(listOf(this, stroke))
}

internal fun rectPath(x: Float, y: Float, width: Float, height: Float): UiPath {
    if (width <= 0f || height <= 0f) return UiPath.Empty
    return path {
        moveTo(x, y)
        lineTo(x + width, y)
        lineTo(x + width, y + height)
        lineTo(x, y + height)
        close()
    }
}

internal fun UiPath.intersectedWith(mask: UiPath): UiPath {
    if (isEmpty() || mask.isEmpty()) return UiPath.Empty
    val area = Area(toAwtPath())
    area.intersect(Area(mask.toAwtPath()))
    return area.toUiPath()
}

internal fun UiPath.unionWith(path: UiPath): UiPath {
    if (isEmpty()) return path
    if (path.isEmpty()) return this
    val area = Area(toAwtPath())
    area.add(Area(path.toAwtPath()))
    return area.toUiPath()
}

internal fun UiPath.toAwtPath(): Path2D.Float {
    val path = Path2D.Float(Path2D.WIND_NON_ZERO)
    var current = UiPathPoint(0f, 0f)
    var subPathStart = UiPathPoint(0f, 0f)
    commands.forEach { command ->
        when (command) {
            is UiPathCommand.MoveTo -> {
                path.moveTo(command.target.x, command.target.y)
                current = command.target
                subPathStart = command.target
            }
            is UiPathCommand.LineTo -> {
                path.lineTo(command.target.x, command.target.y)
                current = command.target
            }
            is UiPathCommand.CubicTo -> path.curveTo(
                command.control1.x,
                command.control1.y,
                command.control2.x,
                command.control2.y,
                command.target.x,
                command.target.y,
            ).also { current = command.target }
            is UiPathCommand.QuadraticTo -> path.quadTo(
                command.control.x,
                command.control.y,
                command.target.x,
                command.target.y,
            ).also { current = command.target }
            is UiPathCommand.ArcTo -> {
                val arcPath = UiPath(listOf(UiPathCommand.MoveTo(current), command)).flatten().toPath()
                arcPath.commands.forEach { arcCommand ->
                    if (arcCommand is UiPathCommand.LineTo) path.lineTo(arcCommand.target.x, arcCommand.target.y)
                }
                current = command.target
            }
            UiPathCommand.Close -> {
                path.closePath()
                current = subPathStart
            }
        }
    }
    return path
}

internal fun AwtShape.toUiPath(flatness: Double? = null): UiPath {
    val builder = UiPathBuilder()
    val iterator = if (flatness == null) getPathIterator(null) else getPathIterator(null, flatness)
    val coordinates = FloatArray(6)
    while (!iterator.isDone) {
        when (iterator.currentSegment(coordinates)) {
            PathIterator.SEG_MOVETO -> builder.moveTo(coordinates[0], coordinates[1])
            PathIterator.SEG_LINETO -> builder.lineTo(coordinates[0], coordinates[1])
            PathIterator.SEG_QUADTO -> builder.quadraticBezierTo(
                coordinates[0],
                coordinates[1],
                coordinates[2],
                coordinates[3],
            )
            PathIterator.SEG_CUBICTO -> builder.curveTo(
                coordinates[0],
                coordinates[1],
                coordinates[2],
                coordinates[3],
                coordinates[4],
                coordinates[5],
            )
            PathIterator.SEG_CLOSE -> builder.close()
        }
        iterator.next()
    }
    return builder.build()
}

private fun UiColor.withAlphaMultiplier(multiplier: Float): UiColor {
    return copy(alpha = alpha * multiplier.coerceAtLeast(0f))
}

private fun UiSvgStrokeLineCap.toAwtStrokeCap(): Int {
    return when (this) {
        UiSvgStrokeLineCap.BUTT -> BasicStroke.CAP_BUTT
        UiSvgStrokeLineCap.ROUND -> BasicStroke.CAP_ROUND
        UiSvgStrokeLineCap.SQUARE -> BasicStroke.CAP_SQUARE
    }
}

private fun UiSvgStrokeLineJoin.toAwtStrokeJoin(): Int {
    return when (this) {
        UiSvgStrokeLineJoin.MITER -> BasicStroke.JOIN_MITER
        UiSvgStrokeLineJoin.ROUND -> BasicStroke.JOIN_ROUND
        UiSvgStrokeLineJoin.BEVEL -> BasicStroke.JOIN_BEVEL
    }
}
