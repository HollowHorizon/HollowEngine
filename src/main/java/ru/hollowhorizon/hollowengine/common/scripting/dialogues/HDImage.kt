package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent.blit
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.toRL

class HDImage(image: String) : HDObject() {
    var alpha: Float = 1.0f
    private val image: ResourceLocation
    var animate = true
    var width = 200
    var height = 200

    init {
        this.image = image.toRL()
    }

    @Suppress("DEPRECATION")
    fun render(stack: PoseStack, screenWidth: Int, screenHeight: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha)
        val w: Int = (width * scale).toInt()
        val h: Int = (height * scale).toInt()

        Minecraft.getInstance().textureManager.bindForSetup(image)
        blit(
            stack,
            ((screenWidth / 2 - w / 2) + translate.x).toInt(),
            ((screenHeight / 2 - h / 2) + translate.y).toInt(),
            0F, 0F, w, h, w, h
        )
    }
}