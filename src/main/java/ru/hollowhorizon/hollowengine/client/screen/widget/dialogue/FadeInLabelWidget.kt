package ru.hollowhorizon.hollowengine.client.screen.widget.dialogue

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.StringTextComponent
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.GuiAnimator
import ru.hollowhorizon.hc.client.utils.ScissorUtil
import ru.hollowhorizon.hc.client.utils.math.Interpolation


class FadeInLabelWidget(x: Int, y: Int, width: Int, height: Int) : HollowWidget(x, y, width, height, StringTextComponent("")) {
    private var text: ITextComponent = StringTextComponent("")
    private var onFadeInComplete: Runnable? = null
    private var animator: GuiAnimator? = null
    private var isUpdated = false
    override fun init() {
        super.init()
        animator = GuiAnimator.Single(0, width, 1f, Interpolation::easeOutSine)
    }

    fun setText(text: ITextComponent) {
        this.text = text
    }

    fun setText(text: String) {
        setText(StringTextComponent(text))
    }

    fun reset() {
        animator?.reset()
    }

    fun onFadeInComplete(onFadeInComplete: Runnable) {
        this.onFadeInComplete = onFadeInComplete
    }

    override fun renderButton(stack: MatrixStack, mouseX: Int, mouseY: Int, ticks: Float) {
        super.renderButton(stack, mouseX, mouseY, ticks)
        ScissorUtil.start(x, y, animator!!.value, height)
        stack.pushPose()
        stack.translate(0.0, 0.0, 500.0)
        font.drawShadow(stack, text, x.toFloat(), y + height / 2f - font.lineHeight / 2f, 0xFFFFFF)
        stack.popPose()
        ScissorUtil.stop()
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
