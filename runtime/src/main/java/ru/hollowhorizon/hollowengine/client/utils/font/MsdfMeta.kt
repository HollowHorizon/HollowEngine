package ru.hollowhorizon.hollowengine.client.utils.font

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class MsdfMeta(
    val atlas: MsdfAtlasInfo,
    val name: String,
    val metrics: MsdfMetrics,
    val glyphs: List<MsdfGlyph> = emptyList(),
    val compactGlyphs: List<MsdfCompactGlyph> = emptyList(),
    val kerning: List<MsdfKerning> = emptyList(),
)

@Serializable
data class MsdfAtlasInfo(
    val type: String,
    val distanceRange: Float,
    val size: Float,
    val width: Int,
    val height: Int,
    val yOrigin: String,
)

@Serializable
data class MsdfMetrics(
    val emSize: Float,
    val lineHeight: Float,
    val ascender: Float,
    val descender: Float,
    val underlineY: Float,
    val underlineThickness: Float,
)

@Serializable
data class MsdfCompactGlyph(
    val cd: Int,
    val adv: Float,
    val pB: List<Int>,
    val aB: List<Int>,
) {
    fun toMsdfGlyph(): MsdfGlyph {
        return MsdfGlyph(
            unicode = cd,
            advance = adv,
            planeBounds = MsdfRect(
                pB[0] / 10000f, pB[1] / 10000f, pB[2] / 10000f, pB[3] / 10000f
            ),
            atlasBounds = MsdfRect(
                aB[0] + 0.5f, aB[1] + 0.5f, aB[2] + 0.5f, aB[3] + 0.5f
            )
        )
    }

    companion object {
        fun fromMsdfGlyph(g: MsdfGlyph): MsdfCompactGlyph {
            return MsdfCompactGlyph(
                cd = g.unicode,
                adv = g.advance,
                pB = listOf(
                    (g.planeBounds.left * 10000).roundToInt(),
                    (g.planeBounds.bottom * 10000).roundToInt(),
                    (g.planeBounds.right * 10000).roundToInt(),
                    (g.planeBounds.top * 10000).roundToInt()
                ),
                aB = listOf(
                    g.atlasBounds.left.toInt(),
                    g.atlasBounds.bottom.toInt(),
                    g.atlasBounds.right.toInt(),
                    g.atlasBounds.top.toInt()
                )
            )
        }
    }
}


@Serializable
data class MsdfKerning(
    val unicode1: Int,
    val unicode2: Int,
    val advance: Float,
)