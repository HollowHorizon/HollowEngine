package ru.hollowhorizon.hollowstory.client.gui.widget.action

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.util.text.StringTextComponent
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hollowstory.HollowStory.MODID

open class ActionWidget(var ox: Int, var oy: Int, w: Int, h: Int) :
    HollowWidget(ox, oy, w, h, StringTextComponent("ACTION_WIDGET")) {
    var inputWidget: ConnectPointWidget = ConnectPointWidget(this.x, this.y + this.height / 2, 0xFF00FFFF.toInt())
    var outputWidget: ConnectPointWidget =
        ConnectPointWidget(this.x + this.width, this.y + this.height / 2, 0xFF00FF00.toInt())

    init {
        this.addWidget(inputWidget)
        this.addWidget(outputWidget)
    }

    override fun render(stack: MatrixStack, p_230430_2_: Int, p_230430_3_: Int, p_230430_4_: Float) {
        this.bind(MODID, "gui/widgets/element_panel.png")
        this.betterBlit(stack, Alignment.CENTER, 0, 0, this.width, this.height)

        super.render(stack, p_230430_2_, p_230430_3_, p_230430_4_)
    }

    open fun canSee(width: Int, height: Int): Boolean {
        return this.x + this.width > 0 && this.x < width && this.y + this.height > 0 && this.y < height
    }
}