package ru.hollowhorizon.hollowengine.client.utils.font

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The face the bake tests measure against, read straight off the test classpath. */
private const val TestFontResource = "assets/hollowengine/fonts/noto_sans.ttf"

/**
 * Opens the test face, or skips the calling test when FreeType cannot be loaded, the natives are
 * present in a normal test run, but the suite should not hard-fail where they are not.
 */
internal fun openTestFace(): TtfFace? {
    val bytes = object {}.javaClass.classLoader.getResourceAsStream(TestFontResource)?.readBytes()
    assumeTrue(bytes != null, "noto_sans.ttf is on the classpath")
    return runCatching { TtfFace.open(bytes!!) }
        .onFailure { assumeTrue(false, "FreeType is not loadable headlessly: ${it.message}") }
        .getOrNull()
}

/** A square contour, counter-clockwise in this y-up space. */
internal fun squareContour(left: Float, bottom: Float, size: Float): MsdfContour {
    val right = left + size
    val top = bottom + size
    return MsdfContour().apply {
        edges += MsdfEdge(floatArrayOf(left, bottom, right, bottom))
        edges += MsdfEdge(floatArrayOf(right, bottom, right, top))
        edges += MsdfEdge(floatArrayOf(right, top, left, top))
        edges += MsdfEdge(floatArrayOf(left, top, left, bottom))
    }
}

/**
 * A single square, oriented and colored the way the baker would hand it to the generator. Winding
 * it the other way is what pins that orientation down rather than being handed the right answer.
 */
internal fun squareShape(
    left: Float,
    bottom: Float,
    size: Float,
    counterClockwise: Boolean = true,
): MsdfShape = MsdfShape().apply {
    contours += squareContour(left, bottom, size).also { if (!counterClockwise) it.reverse() }
    orientForPositiveInside()
    colorEdges()
}

/** Median of the three channels at a texel of a `width`-wide RGB field. */
internal fun FloatArray.medianAt(x: Int, y: Int, width: Int): Float {
    val base = (y * width + x) * 3
    return msdfMedian(this[base], this[base + 1], this[base + 2])
}

/** Spread between the highest and lowest channel at a texel, how much the channels disagree. */
internal fun FloatArray.channelSpreadAt(x: Int, y: Int, width: Int): Float {
    val base = (y * width + x) * 3
    val red = this[base]
    val green = this[base + 1]
    val blue = this[base + 2]
    return max(red, max(green, blue)) - min(red, min(green, blue))
}

/**
 * Whether `(x, y)` is inside the shape by the non-zero winding rule, TrueType's own fill rule, and
 * the oracle the field is checked against. Deliberately a second implementation rather than a call
 * into [correctMsdfSigns]' scanline: sharing it would let one bug satisfy both sides.
 */
internal fun MsdfShape.containsByWinding(x: Float, y: Float): Boolean {
    var winding = 0
    for (contour in contours) {
        for (edge in contour.edges) {
            val points = edge.points
            var index = 0
            while (index + 3 < points.size) {
                val y0 = points[index + 1]
                val y1 = points[index + 3]
                val crossesUp = y0 <= y && y1 > y
                val crossesDown = y1 <= y && y0 > y
                if (crossesUp || crossesDown) {
                    val t = (y - y0) / (y1 - y0)
                    if (points[index] + t * (points[index + 2] - points[index]) > x) {
                        winding += if (crossesUp) 1 else -1
                    }
                }
                index += 2
            }
        }
    }
    return winding != 0
}

/** Unsigned distance from `(x, y)` to the nearest point of the outline, in shape units. */
internal fun MsdfShape.distanceToOutline(x: Float, y: Float): Float {
    var best = Float.MAX_VALUE
    for (contour in contours) {
        for (edge in contour.edges) {
            val points = edge.points
            var index = 0
            while (index + 3 < points.size) {
                val x0 = points[index]
                val y0 = points[index + 1]
                val dx = points[index + 2] - x0
                val dy = points[index + 3] - y0
                val lengthSquared = dx * dx + dy * dy
                val t = if (lengthSquared > 0f) {
                    (((x - x0) * dx + (y - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val ex = x0 + dx * t - x
                val ey = y0 + dy * t - y
                best = min(best, sqrt(ex * ex + ey * ey))
                index += 2
            }
        }
    }
    return best
}

/** Median of the three channels at one atlas texel, as a byte-valued `0..255`. */
internal fun BakedMsdfAtlas.medianAt(x: Int, y: Int): Int {
    val base = (y * width + x) * 3
    val red = pixels[base].toInt() and 0xFF
    val green = pixels[base + 1].toInt() and 0xFF
    val blue = pixels[base + 2].toInt() and 0xFF
    return max(min(red, green), min(max(red, green), blue))
}

/** Bilinear sample of the median of RGB at a texel coordinate, the way the shader reads the atlas. */
internal fun BakedMsdfAtlas.sampleMedian(tx: Float, ty: Float): Float {
    val x0 = kotlin.math.floor(tx - 0.5f).toInt().coerceIn(0, width - 1)
    val y0 = kotlin.math.floor(ty - 0.5f).toInt().coerceIn(0, height - 1)
    val x1 = min(x0 + 1, width - 1)
    val y1 = min(y0 + 1, height - 1)
    val fx = (tx - 0.5f - x0).coerceIn(0f, 1f)
    val fy = (ty - 0.5f - y0).coerceIn(0f, 1f)
    val channels = FloatArray(3)
    for (channel in 0..2) {
        fun texel(x: Int, y: Int) = (pixels[(y * width + x) * 3 + channel].toInt() and 0xFF) / 255f
        val top = texel(x0, y0) + (texel(x1, y0) - texel(x0, y0)) * fx
        val bottom = texel(x0, y1) + (texel(x1, y1) - texel(x0, y1)) * fx
        channels[channel] = top + (bottom - top) * fy
    }
    return msdfMedian(channels[0], channels[1], channels[2])
}
