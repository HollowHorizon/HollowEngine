package ru.hollowhorizon.hollowengine.client.screen.widget.box

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.util.text.ITextComponent
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.toSTC

class TextBox(
    x: Int, y: Int, width: Int, height: Int,
    var text: ITextComponent = "".toSTC(),
    var color: Int = 0xFFFFFF,
    val shade: Boolean = true,
    val anchor: Anchor = Anchor.MIDDLE
) : HollowWidget(x, y, width, height, "".toSTC()) {
    override fun renderButton(stack: MatrixStack, mouseX: Int, mouseY: Int, ticks: Float) {
        super.renderButton(stack, mouseX, mouseY, ticks)
        val x =
            x + if (anchor == Anchor.START) 0 else if (anchor == Anchor.MIDDLE) width / 2 - font.width(text) / 2 else width - font.width(
                text
            )
        val y = y + height / 2 - font.lineHeight / 2;

        stack.pushPose()
        stack.translate(0.0, 0.0, 100.0)
        if (shade) {
            font.drawShadow(stack, text, x.toFloat() + 1, y.toFloat() + 1, color)
        } else {
            font.draw(stack, text, x.toFloat(), y.toFloat(), color)
        }
        stack.popPose()
    }
}