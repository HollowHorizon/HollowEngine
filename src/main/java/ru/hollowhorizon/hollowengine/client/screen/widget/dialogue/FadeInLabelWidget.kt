package ru.hollowhorizon.hollowengine.client.screen.widget.dialogue

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.GuiAnimator
import ru.hollowhorizon.hc.client.utils.ScissorUtil
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.mcText


class FadeInLabelWidget(x: Int, y: Int, width: Int, height: Int) : HollowWidget(x, y, width, height, "".mcText) {
    private var text = "".mcText
    private var onFadeInComplete: Runnable? = null
    private var animator: GuiAnimator? = null
    private var isUpdated = false
    override fun init() {
        super.init()
        animator = GuiAnimator.Single(0, width, 1f, Interpolation.EXPO_OUT.function)
    }

    fun setText(text: Component) {
        this.text = text
    }

    fun setText(text: String) {
        setText(text.mcText)
    }

    fun reset() {
        animator?.reset()
    }

    fun onFadeInComplete(onFadeInComplete: Runnable) {
        this.onFadeInComplete = onFadeInComplete
    }

    override fun renderButton(stack: PoseStack, mouseX: Int, mouseY: Int, ticks: Float) {
        super.renderButton(stack, mouseX, mouseY, ticks)
        ScissorUtil.push(x, y, animator!!.value, height)
        stack.pushPose()
        stack.translate(0.0, 0.0, 500.0)
        font.drawShadow(stack, text, x.toFloat(), y + height / 2f - font.lineHeight / 2f, 0xFFFFFF)
        stack.popPose()
        ScissorUtil.pop()
    }

    override fun tick() {
        if (animator?.isFinished() == true && !isUpdated) {
            onFadeInComplete?.run()
            isUpdated = true
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    val isComplete: Boolean
        get() = animator!!.isFinished()

    fun complete() {
        animator!!.setTime(1.0f)
    }

    fun getText(): String {
        return text.string
    }
}
