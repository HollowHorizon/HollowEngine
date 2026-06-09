package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

@Serializable(with = ColorHSVSerializer::class)
class ColorHSV() {
    @Transient
    val hue = mutableStateOf(0f)

    @Transient
    val sat = mutableStateOf(1f)

    @Transient
    val value = mutableStateOf(1f)

    @Transient
    val alpha = mutableStateOf(1f)

    @Transient
    val hexString = mutableStateOf("")

    fun toColor(): Color {
        return Color.Hsv(hue.value, sat.value, value.value).toSrgb(a = alpha.value)
    }

    private val popup = AutoPopup()

    context(scope: UiScope)
    operator fun invoke(scale: Float): Unit = with(scope) {
        Box(Dimensions.PaddingLarge * scale, Dimensions.PaddingLarge * scale) {
            modifier.background(RoundRectBackground(toColor(), Dimensions.PaddingSmall * scale))
                .zLayer(modifier.zLayer + 800)
                .border(RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall * scale, Dimensions.PaddingSmall * scale))
                .onClick {
                    popup.popupContent = {
                        modifier.padding(Dimensions.PaddingSmall * scale)
                            .zLayer(100_100_100)

                        ColorChooserH(
                            hue = hue,
                            saturation = sat,
                            value = value,
                            alpha = alpha,
                            hexString = hexString
                        )
                    }
                    popup.show(Vec2f(uiNode.rightPx + Dimensions.PaddingSmall.px, uiNode.topPx))
                }
                .alignY(AlignmentY.Center)
        }
        popup()
    }
}

fun Color.toIntRGB(): Int {
    val r = (this.r * 255).toInt() and 0xFF
    val g = (this.g * 255).toInt() and 0xFF
    val b = (this.b * 255).toInt() and 0xFF
    return r shl 16 or (g shl 8) or b
}
