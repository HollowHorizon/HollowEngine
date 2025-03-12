package ru.hollowhorizon.hollowengine.client.gui.scripting.theme

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.Colors
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.Sizes
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.MsdfFont.Companion.MSDF_TEX_PROPS
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hc.common.utils.json.JsonFormat
import ru.hollowhorizon.hc.common.utils.rl

object IdeTheme {
    @OptIn(ExperimentalSerializationApi::class)
    private val font by lazy {
        val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/pt_sans.json".rl.stream)
        val msdfMap = Texture2d(MSDF_TEX_PROPS, "MsdfFont:${fontInfo.name}") {
            Assets.loadImage2d("hollowengine:fonts/pt_sans.png", MSDF_TEX_PROPS).getOrThrow()
        }
        MsdfFontData(msdfMap, fontInfo)
    }

    val sizes = Sizes.large.copy(
        normalText = MsdfFont(font, 18f),
        borderWidth = Dp.roundToWholePx(1.5f)
    )
    var colors = Colors.darkColors(
        background = Color("24272EFF"),
        backgroundVariant = Color("1F2228FF"),
        secondaryVariant = Color("3C3C4AFF"),
        secondary = Color("4C4C5AFF")
    )
    val hoveredColors = Colors.darkColors(
        background = Color("31343DFF")
    )
}