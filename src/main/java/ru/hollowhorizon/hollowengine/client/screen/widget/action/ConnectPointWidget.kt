package ru.hollowhorizon.hollowengine.client.screen.widget.action

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.util.text.StringTextComponent
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget

open class ConnectPointWidget(x: Int, y: Int, private val color: Int) : HollowWidget(x - 4, y - 4, 8, 8, StringTextComponent("CONNECT_POINT")) {

    override fun render(stack: MatrixStack, p_230430_2_: Int, p_230430_3_: Int, p_230430_4_: Float) {
        fill(stack, x, y, x + width, y + height, 0xFFFFFFFF.toInt())
        fill(stack, x + 1, y + 1, x + width - 1, y + height - 1, color)
    }

    open fun connect() {
    }
}