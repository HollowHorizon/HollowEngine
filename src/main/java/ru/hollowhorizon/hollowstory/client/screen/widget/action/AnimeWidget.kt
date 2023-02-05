package ru.hollowhorizon.hollowstory.client.screen.widget.action

import com.mojang.blaze3d.matrix.MatrixStack
import com.mojang.blaze3d.systems.RenderSystem
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.HollowStory.MODID

class AnimeWidget(x:Int, y:Int, w:Int, h:Int) : HollowWidget(x, y, w, h, "".toSTC()) {
    override fun renderButton(stack: MatrixStack, mouseX: Int, mouseY: Int, ticks: Float) {
        bind(MODID, "gui/anime.png")
        RenderSystem.defaultAlphaFunc()
        RenderSystem.defaultBlendFunc()
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 0.6F)
        blit(stack, x, y, 0F, 0F, width, height, width, height)
        super.renderButton(stack, mouseX, mouseY, ticks)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }
}