package ru.hollowhorizon.hollowengine.client.gui.scripting.theme

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.Colors
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.Sizes
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat

object IdeTheme {

    val sizes = Sizes.large.copy(
        normalText = MsdfFont(ColorTheme.Fonts.MONOCRAFT, Dimensions.FontNormal),
        smallText = MsdfFont(ColorTheme.Fonts.MONOCRAFT, Dimensions.FontSmall),
        largeText = MsdfFont(ColorTheme.Fonts.MONOCRAFT, Dimensions.FontLarge),
        borderWidth = Dp.roundToWholePx(1.5f)
    )
    var colors = Colors.darkColors()
}

fun loadFont(font: ResourceLocation): MsdfFontData {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>(font.stream)
    val msdfMap = Texture2d(
        TexFormat.RGBA,
        mipMapping = MipMapping.Off,
        samplerSettings = SamplerSettings(),
        "MsdfFont:${fontInfo.name}"
    ) {
        Assets.loadImage2d(font.withPath(font.path.removeSuffix(".json") + ".png").toString())
            .getOrDefault(SingleColorTexture.getColorTextureData(Color.BLACK))
    }
    return MsdfFontData(msdfMap, fontInfo)
}