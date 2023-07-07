package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import com.mojang.blaze3d.matrix.MatrixStack
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.AbstractGui
import net.minecraft.util.ResourceLocation
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
    fun render(stack: MatrixStack, screenWidth: Int, screenHeight: Int) {
        RenderSystem.enableAlphaTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultAlphaFunc()
        RenderSystem.defaultBlendFunc()
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, alpha)
        val w: Int = (width * scale).toInt()
        val h: Int = (height * scale).toInt()

        Minecraft.getInstance().textureManager.bind(image)
        AbstractGui.blit(
            stack,
            ((screenWidth / 2 - w / 2) + translate.x).toInt(),
            ((screenHeight / 2 - h / 2) + translate.y).toInt(),
            0F, 0F, w, h, w, h
        )
    }
}