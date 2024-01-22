package ru.hollowhorizon.hollowengine.client.screen.recording

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.screens.widget.LabelWidget
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.client.screen.widget.HollowTextFieldWidget
import ru.hollowhorizon.hollowengine.cutscenes.replay.ToggleRecordingPacket

class StartRecordingScreen : HollowScreen() {

    init {
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true)
    }

    override fun init() {
        super.init()

        box {
            size = 90.pc x 90.pc
            renderer = { stack, x, y, w, h ->
                fill(stack, x, y, x + w, y + h, 0x8800173D.toInt())
                fill(stack, x, y, x + w, y + 2, 0xFF07BBDB.toInt())
                fill(stack, x, y + h - 2, x + w, y + h, 0xFF07BBDB.toInt())
                fill(stack, x, y, x + 2, y + h, 0xFF07BBDB.toInt())
                fill(stack, x + w - 2, y, x + w, y + h, 0xFF07BBDB.toInt())

            }

            elements {
                align = Alignment.CENTER
                spacing = 4.pc x 4.pc
                placementType = PlacementType.GRID

                +LabelWidget(
                    "hollowengine.enter_replay".mcTranslate,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.5f
                )
                //lineBreak()
                val replayName = +HollowTextFieldWidget(
                    font, 0, 0, 90.pc.w().value, 20,
                    "".mcText,
                    "hollowengine:textures/gui/text_field.png".rl
                )

                +LabelWidget(
                    "hollowengine.enter_model_path".mcTranslate,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.2f
                )
                //lineBreak()
                val modelName = +HollowTextFieldWidget(
                    font, 0, 0, 90.pc.w().value, 20,
                    "".mcText,
                    "hollowengine:textures/gui/text_field.png".rl
                )
                modelName.setResponder {
                    if (!ResourceLocation.isValidResourceLocation(it) || !it.rl.exists() ||
                        !(it.endsWith(".gltf") || it.endsWith(".glb"))
                    ) {
                        modelName.setTextColor(0xF54242)
                    } else {
                        modelName.setTextColor(0x42f542)
                    }
                }
                //lineBreak()

                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.start".mcTranslate,
                    {
                        if (modelName.value.rl.exists()) {
                            startRecording(replayName.value, modelName.value)
                            onClose()
                        }
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.cancel".mcTranslate,
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

fun startRecording(replayName: String, modelName: String) {
    Minecraft.getInstance().player!![AnimatedEntityCapability::class].model = modelName
    ToggleRecordingPacket(replayName).send()
    RecordingDriver.resetTime()
    RecordingDriver.enable = true
}