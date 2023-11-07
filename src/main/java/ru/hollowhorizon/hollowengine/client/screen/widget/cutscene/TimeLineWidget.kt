package ru.hollowhorizon.hollowengine.client.screen.widget.cutscene

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.ScissorUtil
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toSTC

class TimeLineWidget(x: Int, y: Int, width: Int, height: Int) : HollowWidget(x, y, width, height, "".toSTC()) {
    private var isLeftKeyDown: Boolean = false
    private var cursorX = -1

    override fun renderButton(stack: PoseStack, mouseX: Int, mouseY: Int, ticks: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        if (isHovered && isLeftKeyDown || cursorX == -1) cursorX = mouseX - x

        ScissorUtil.push(x, y, width, height)
        textureManager.bindForSetup("hollowengine:textures/gui/cutscenes/cursor.png".rl)
        blit(stack, x + cursorX - height / 4, y, 0f, 0f, height / 2, height, height / 2, height)
        ScissorUtil.pop()
    }

    fun getCursorProgress(): Float {
        return cursorX.toFloat() / width
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isLeftKeyDown = button == 0
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isLeftKeyDown = false
        return false
    }
}