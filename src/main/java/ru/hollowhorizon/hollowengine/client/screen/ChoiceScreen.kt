package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.WidgetPlacement
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.ChoiceScreenPacket

object ChoiceScreen : HollowScreen() {
    private var container = ChoiceScreenPacket()

    override fun init() {
        this.clearWidgets()
        container.choices.forEachIndexed { i, choice ->
            addRenderableWidget(
                WidgetPlacement.configureWidget(
                    { x, y, w, h ->
                        Button(x, y, w, h, choice.mcText) {
                            this@ChoiceScreen.init()
                            OnChoicePerform(i).send()
                        }
                    },
                    Alignment.LEFT_CENTER,
                    20 + container.buttonX,
                    this.height / 4 - 25 * i - container.buttonY,
                    this.width,
                    this.height,
                    320,
                    20
                )
            )
        }
        container.choices.clear()
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        RenderSystem.setShaderTexture(0, container.background.rl)
        blit(pPoseStack, 0, 0, 0f, 0f, width, height, width, height)
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)

        font.drawShadow(pPoseStack, container.text, 20f + container.textX, 20f + container.textY, 0xFFFFFF)

        InventoryScreen.renderEntityInInventory(
            (width * 0.89f).toInt(),
            (height * 0.55f).toInt(),
            height / 4,
            width * 0.89f - pMouseX,
            height * 0.4f - pMouseY,
            Minecraft.getInstance().player!!
        )
    }

    fun open(container: ChoiceScreenPacket) {
        Minecraft.getInstance().setScreen(this)
        this.container = container
        init()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    class Button(x: Int, y: Int, w: Int, h: Int, text: Component, onChoice: BaseButton.() -> Unit) :
        BaseButton(x, y, w, h, text, onChoice, "".rl, textColor = 0xFFFFFF, textColorHovered = 0xF5DD2C) {
        override fun render(stack: PoseStack, x: Int, y: Int, f: Float) {
            val isHovered = isCursorAtButton(x, y)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()

            stack.pushPose()
            stack.translate(
                this.x + width / 2.0 - font.width(text) / 2.0,
                this.y + height / 2.0 - font.lineHeight / 2.0,
                700.0
            )

            font.drawShadow(
                stack, Component.literal(text.string).apply {
                    if (isHovered) style = Style.EMPTY.withUnderlined(true)
                },
                0f, 0f,
                if (isHovered) textColorHovered else textColor
            )

            stack.popPose()
        }
    }
}