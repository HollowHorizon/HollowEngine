package ru.hollowhorizon.hollowengine.client.screen

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.client.screen.widget.HollowTextFieldWidget
import ru.hollowhorizon.hollowengine.cutscenes.replay.RecordingPacket

class RecordingScreen : HollowScreen() {

    init {
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true)
    }

    override fun init() {
        super.init()

        box {
            size = 90.pc x 90.pc
            renderer = { stack, x, y, w, h ->
                fill(stack, x, y, x + w, y + h, 0x4400173D)
                font.drawScaled(stack, Anchor.CENTER, "Enter recording name:".mcText, x + w / 2, y + 15, 0xFFFFFF, 1.5f)
            }

            elements {
                align = Alignment.CENTER

                placementType = PlacementType.GRID

                val field = +HollowTextFieldWidget(
                    font, 0, 0, 300, 20,
                    "".mcText,
                    "hollowengine:textures/gui/text_field.png".rl
                )
                +BaseButton(
                    0, 0, 100, 20,
                    "hollowengine.save".mcTranslate,
                    { RecordingPacket(field.value).send(); RecordingDriver.enable = true; onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 100, 20,
                    "hollowengine.start".mcTranslate,
                    { onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
            }
        }
    }

    override fun onClose() {
        super.onClose()
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(false)
    }
}