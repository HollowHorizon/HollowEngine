package ru.hollowhorizon.hollowengine.client.screen.widget.dialogue

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.GuiAnimator
import ru.hollowhorizon.hc.client.utils.ScissorUtil
import ru.hollowhorizon.hc.client.utils.mcText


class DialogueTextBox(x: Int, y: Int, width: Int, height: Int) : HollowWidget(x, y, width, height, "".mcText) {
    var animator: GuiAnimator? = null
    var text: String = ""
        set(value) {
            field = value
            currentLine = 0
            animator?.reset()
        }
    private var currentLine = 0
    private var linesCount = 0
    var complete: Boolean
        get() = currentLine >= linesCount
        set(v) {
            if (v) currentLine = 99
            else {
                currentLine = 0
                animator?.reset()
            }
        }

    override fun init() {
        animator = GuiAnimator.Single(0, width, 2f) { f -> f }
    }

    override fun renderButton(stack: PoseStack, mouseX: Int, mouseY: Int, ticks: Float) {
        super.renderButton(stack, mouseX, mouseY, ticks)

        val lines = font.split(text.mcText, width)
        linesCount = lines.size

        animator?.update(ticks)

        stack.pushPose()
        stack.translate(0.0, 0.0, 500.0)
        lines.forEachIndexed { i, line ->
            val lineWidth = if (i < currentLine) width
            else if (i == currentLine) animator?.value ?: 0
            else 0

            ScissorUtil.push(this.x, this.y + i * font.lineHeight, lineWidth, font.lineHeight)
            font.drawShadow(stack, line, this.x.toFloat(), (this.y + i * font.lineHeight).toFloat(), 0xFFFFFF)
            ScissorUtil.pop()
        }
        stack.popPose()

        if (animator?.isFinished() == true) {
            if (currentLine < linesCount) {
                animator?.reset()
                currentLine++
            }
        }
    }
}
