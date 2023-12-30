package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toRGBA
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hc.common.network.Packet

object OverlayScreen : HollowScreen("".toSTC()) {
    private var text: String = ""
    var subtitle: String = ""
    var color = 0xFFFFFF
    var texture = ""
    private var ticks = 0
    private var maxTicks = 0
    private var fadeType = FadeType.FADE_IN
    var isOverlayMode = false

    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, particalTick: Float) {
        val rgba = color.toRGBA()
        var alpha = ((ClientTickHandler.ticks - ticks + particalTick) / maxTicks.toFloat()).coerceAtMost(1.0f)

        if (fadeType == FadeType.FADE_OUT) {
            alpha = 1f - alpha
            if (alpha == 0f) Minecraft.getInstance().setScreen(null)
        }

        if (isOverlayMode) alpha = 0f

        if(texture.isNotEmpty()) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(rgba.r, rgba.g, rgba.b, alpha)
            RenderSystem.setShaderTexture(0, texture.rl)
            blit(stack, 0, 0, 0f, 0f, width, height, width, height)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        } else {
            fill(stack, 0, 0, width, height, ARGB(alpha, rgba.r, rgba.g, rgba.b).toInt())
        }

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
        if (show) Minecraft.getInstance().setScreen(this)
        else Minecraft.getInstance().setScreen(null)
        isOverlayMode = true
        text = ""
        subtitle = ""
    }

    fun hideGui(bool: Boolean) {
        Minecraft.getInstance().options.hideGui = bool
    }

    fun makeBlack(text: String, subtitle: String, color: Int, texture: String, time: Int) {
        Minecraft.getInstance().setScreen(this)
        isOverlayMode = false
        ticks = ClientTickHandler.ticks
        maxTicks = time
        fadeType = FadeType.FADE_IN
        this.color = color
        this.texture = texture
        this.text = text
        this.subtitle = subtitle
    }

    fun makeTransparent(text: String, subtitle: String, color: Int, texture: String, time: Int) {
        Minecraft.getInstance().setScreen(this)
        isOverlayMode = false
        ticks = ClientTickHandler.ticks
        maxTicks = time
        fadeType = FadeType.FADE_OUT
        this.color = color
        this.texture = texture
        this.text = text
        this.subtitle = subtitle
    }

    override fun shouldCloseOnEsc() = false

    override fun isPauseScreen() = false

    enum class FadeType {
        FADE_IN, FADE_OUT
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class FadeOverlayScreenPacket(
    private val fadeIn: Boolean,
    private val text: String,
    private val subtitle: String,
    private val color: Int,
    private val texture: String,
    private val time: Int
) : HollowPacketV3<FadeOverlayScreenPacket> {
    override fun handle(player: Player, data: FadeOverlayScreenPacket) {
        OverlayScreen.texture = ""
        if (data.fadeIn) OverlayScreen.makeBlack(data.text, data.subtitle, data.color, data.texture, data.time)
        else OverlayScreen.makeTransparent(data.text, data.subtitle, data.color, data.texture, data.time)
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class OverlayScreenPacket(private val enable: Boolean) : HollowPacketV3<OverlayScreenPacket> {
    override fun handle(player: Player, data: OverlayScreenPacket) {
        OverlayScreen.showOverlay(data.enable)
    }

}

data class ARGB(val a: Int, val r: Int, val g: Int, val b: Int) {
    constructor(alpha: Float, r: Float, g: Float, b: Float) : this(
        (alpha * 255).toInt(),
        (r * 255).toInt(),
        (g * 255).toInt(),
        (b * 255).toInt()
    )
    fun toInt() = (a shl 24) or (r shl 16) or (g shl 8) or b
}