package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

object OverlayScreen : HollowScreen("".toSTC()) {
    private var text: String = ""
    var subtitle: String = ""
    private var ticks = 0
    private var maxTicks = 0
    private var fadeType = FadeType.FADE_IN
    var isOverlayMode = false

    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, particalTick: Float) {
        var alpha = ((ClientTickHandler.ticks - ticks + particalTick) / maxTicks.toFloat() * 0xFF)
            .coerceAtMost(255F).toInt()

        if (fadeType == FadeType.FADE_OUT) {
            alpha = 255 - alpha
            hideGui(false)
            if (alpha == 0) Minecraft.getInstance().setScreen(null)
        }

        if(isOverlayMode) alpha = 0

        fill(stack, 0, 0, this.width, this.height, ARGB(alpha, 0, 0, 0).toInt())

        if (text.isNotEmpty()) font.drawScaled(
            stack,
            Anchor.CENTER,
            text.toSTC(),
            this.width / 2,
            this.height / 3,
            0xFFFFFF,
            3.0F
        )
        if (subtitle.isNotEmpty()) font.drawScaled(
            stack, Anchor.CENTER,
            subtitle.toSTC(),
            this.width / 2,
            this.height / 3 + font.lineHeight * 3,
            0xFFFFFF,
            1.5F
        )

        super.render(stack, mouseX, mouseY, particalTick)
    }

    fun showOverlay(show: Boolean) {
        hideGui(show)
        if(show) Minecraft.getInstance().setScreen(this)
        else Minecraft.getInstance().setScreen(null)
        isOverlayMode = true
        text = ""
        subtitle = ""
    }

    fun hideGui(bool: Boolean) {
        Minecraft.getInstance().options.hideGui = bool
    }

    fun makeBlack(text: String, subtitle: String, time: Int) {
        Minecraft.getInstance().setScreen(this)
        hideGui(true)
        isOverlayMode = false
        ticks = ClientTickHandler.ticks
        maxTicks = time
        fadeType = FadeType.FADE_IN
        this.text = text
        this.subtitle = subtitle
    }

    fun makeTransparent(text: String, subtitle: String, time: Int) {
        Minecraft.getInstance().setScreen(this)
        hideGui(true)
        isOverlayMode = false
        ticks = ClientTickHandler.ticks
        maxTicks = time
        fadeType = FadeType.FADE_OUT
        this.text = text
        this.subtitle = subtitle
    }

    override fun shouldCloseOnEsc() = false

    override fun isPauseScreen() = false

    enum class FadeType {
        FADE_IN, FADE_OUT
    }
}

@Serializable
class OverlayScreenContainer(
    val fadeIn: Boolean,
    val text: String,
    val subtitle: String,
    val time: Int
)

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class FadeOverlayScreenPacket : Packet<OverlayScreenContainer>({ player, value ->
    if (value.fadeIn) OverlayScreen.makeBlack(value.text, value.subtitle, value.time)
    else OverlayScreen.makeTransparent(value.text, value.subtitle, value.time)
})

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class OverlayScreenPacket : Packet<Boolean>({ player, value ->
    OverlayScreen.showOverlay(value)
})

data class ARGB(val a: Int, val r: Int, val g: Int, val b: Int) {
    fun toInt() = (a shl 24) or (r shl 16) or (g shl 8) or b
}