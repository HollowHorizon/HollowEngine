package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable(with = ColorHSVSerializer::class)
class ColorHSV() : Composable {
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

    override fun UiScope.compose() {
        Box(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f) {
            modifier.background(RoundRectBackground(toColor(), sizes.smallGap))
                .zLayer(800)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))
                .onClick {
                    popup.popupContent = {
                        modifier.padding(sizes.smallGap)

                        ColorChooserH(
                            hue = hue,
                            saturation = sat,
                            value = value,
                            alpha = alpha,
                            hexString = hexString
                        )
                    }
                    popup.show(Vec2f(uiNode.rightPx + sizes.smallGap.px, uiNode.topPx))
                }
        }
        popup()
    }
}

fun Color.toIntRGB(): Int {
    val r = (this.r * 255).toInt() and 0xFF
    val g = (this.g * 255).toInt() and 0xFF
    val b = (this.b * 255).toInt() and 0xFF
    return (r shl 16) or (g shl 8) or b
}
