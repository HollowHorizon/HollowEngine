package ru.hollowhorizon.hollowengine.client.screen.recording

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.api.IAutoScaled
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.cutscenes.replay.PauseRecordingPacket
import ru.hollowhorizon.hollowengine.cutscenes.replay.RecordingContainer
import ru.hollowhorizon.hollowengine.cutscenes.replay.ToggleRecordingPacket

class ModifyRecordingScreen : HollowScreen(), IAutoScaled {

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
                alignElements = Alignment.CENTER
                spacing = 2.pc x 4.pc
                placementType = PlacementType.GRID

                +BaseButton(
                    0, 0, 45.pc.w().value, 20,
                    "hollowengine.play_animation".mcTranslate,
                    {
                        PlayAnimationScreen(true).open()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )

                +BaseButton(
                    0, 0, 44.pc.w().value, 20,
                    "hollowengine.stop_animation".mcTranslate,
                    {
                        PlayAnimationScreen.ChoiceScreen("Выберите анимации из списка:",
                            GltfManager.getOrCreate(Minecraft.getInstance().player!![AnimatedEntityCapability::class].model.rl).modelTree.animations
                                .map { it.name ?: "Unnamed" }
                                .map {
                                    BaseButton(
                                        0, 0, 60.pc.w().value, 20, it.mcText,
                                        {
                                            PauseRecordingPacket(
                                                true, RecordingContainer(
                                                    "%STOP%$it", LayerMode.ADD, PlayMode.LOOPED, 1f
                                                )
                                            ).send()
                                            Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers.removeIf { anim -> anim.animation == it }
                                            RecordingDriver.enable = true
                                            onClose()
                                        },
                                        "hollowengine:textures/gui/long_button.png".rl
                                    )
                                }
                        ).open()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )

                +BaseButton(
                    0, 0, 29.pc.w().value, 20,
                    "hollowengine.save".mcTranslate,
                    {
                        RecordingDriver.enable = false
                        Minecraft.getInstance().player!![AnimatedEntityCapability::class].model = "%NO_MODEL%"
                        Minecraft.getInstance().player!![AnimatedEntityCapability::class].animations.clear()
                        Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers.clear()
                        ToggleRecordingPacket("").send()
                        onClose()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 29.pc.w().value, 20,
                    "hollowengine.pause".mcTranslate,
                    {
                        onClose()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 29.pc.w().value, 20,
                    "hollowengine.cancel".mcTranslate,
                    { RecordingDriver.enable = true; onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
            }
        }
    }
}
