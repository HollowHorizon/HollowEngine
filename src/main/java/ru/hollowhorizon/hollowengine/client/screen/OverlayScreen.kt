package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.vertex.PoseStack
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.toSTC

class OverlayScreen(val text: String? = null) : HollowScreen("".toSTC()) {
    private var alpha = 0xFF
    private var ticks = 0
    private var maxTicks = 0
    private var fadeType = FadeType.FADE_IN
    var subTitle: String? = null

    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, particalTick: Float) {

        fill(stack, 0, 0, this.width, this.height, ARGB(alpha, 0, 0, 0).toInt())

        if (text != null) font.drawScaled(stack, Anchor.CENTER, text.toSTC(), this.width / 2, this.height / 3, 0xFFFFFF, 3.0F)
        if (subTitle != null) font.drawScaled(stack, Anchor.CENTER,
            subTitle!!.toSTC(),
            this.width / 2,
            this.height / 3 + font.lineHeight * 3,
            0xFFFFFF,
            1.5F
        )

        if (ticks > 0) {
            when (fadeType) {
                FadeType.FADE_IN -> {
                    alpha -= ((maxTicks - ticks) / maxTicks.toFloat() * 0xFF).toInt()
                    if (alpha <= 0) {
                        alpha = 0
                    }
                    ticks--
                }

                FadeType.FADE_OUT -> {
                    alpha += ((maxTicks - ticks) / maxTicks.toFloat() * 0xFF).toInt()
                    if (alpha >= 0xFF) {
                        alpha = 0xFF
                    }
                    ticks--
                }
            }
        }

        super.render(stack, mouseX, mouseY, particalTick)
    }

    fun makeBlack(time: Float) {
        ticks = (time * 60).toInt()
        maxTicks = ticks

        alpha = 0x0

        fadeType = FadeType.FADE_OUT
    }

    fun makeTransparent(time: Float) {
        ticks = (time * 60).toInt()
        maxTicks = ticks

        alpha = 0xFF

        fadeType = FadeType.FADE_IN
    }

    override fun isPauseScreen() = false

    enum class FadeType {
        FADE_IN, FADE_OUT
    }
}

data class ARGB(val a: Int, val r: Int, val g: Int, val b: Int) {
    fun toInt() = (a shl 24) or (r shl 16) or (g shl 8) or b
}