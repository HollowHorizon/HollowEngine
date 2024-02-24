package ru.hollowhorizon.hollowengine.client.screen.recording

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hc.api.IAutoScaled
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hc.common.ui.Anchor
import ru.hollowhorizon.hc.client.screens.widget.LabelWidget
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.overlays.RecordingDriver
import ru.hollowhorizon.hollowengine.client.screen.widget.HollowTextFieldWidget
import ru.hollowhorizon.hollowengine.common.scripting.item
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.waitForgeEvent
import ru.hollowhorizon.hollowengine.cutscenes.replay.PauseRecordingPacket
import ru.hollowhorizon.hollowengine.cutscenes.replay.RecordingContainer
import java.lang.Float
import java.util.regex.Pattern

class PlayAnimationScreen(val play: Boolean) : HollowScreen(), IAutoScaled {
    lateinit var animation: BaseButton
    lateinit var layerMode: BaseButton
    lateinit var playMode: BaseButton
    lateinit var speed: HollowTextFieldWidget
    var animText = "Анимации"
    var layerText = "Слой"
    var playText = "Режим"

    override fun init() {
        super.init()

        box {
            align = Alignment.CENTER
            padding = 5.pc x 5.pc
            alignElements = Alignment.LEFT_CENTER
            spacing = 2.pc x 4.pc
            placementType = PlacementType.GRID
            size = 90.pc x 90.pc

            renderer = { stack, x, y, w, h ->
                fill(stack, x, y, x + w, y + h, 0x8800173D.toInt())
                fill(stack, x, y, x + w, y + 2, 0xFF07BBDB.toInt())
                fill(stack, x, y + h - 2, x + w, y + h, 0xFF07BBDB.toInt())
                fill(stack, x, y, x + 2, y + h, 0xFF07BBDB.toInt())
                fill(stack, x + w - 2, y, x + w, y + h, 0xFF07BBDB.toInt())
            }



            elements {
                +LabelWidget(
                    "hollowengine.choose_animation".mcTranslate,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.1f
                )

                animation = +BaseButton(
                    0, 0, 20.pc.w().value, 20, animText.mcText,
                    {
                        ChoiceScreen("Выберите анимации из списка:",
                            GltfManager.getOrCreate(Minecraft.getInstance().player!![AnimatedEntityCapability::class].model.rl).modelTree.animations
                                .map { it.name ?: "Unnamed" }
                                .map {
                                    BaseButton(
                                        0, 0, 60.pc.w().value, 20, it.mcText,
                                        {
                                            animText = it
                                            Minecraft.getInstance().setScreen(this@PlayAnimationScreen)
                                        },
                                        "hollowengine:textures/gui/long_button.png".rl
                                    )
                                }
                        ).open()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )

                +LabelWidget(
                    "Выберите режим слоя анимации:".mcText,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.1f
                )

                layerMode = +BaseButton(
                    0, 0, 20.pc.w().value, 20, layerText.mcText,
                    {
                        ChoiceScreen("Выберите режим слоя анимации:",
                            arrayListOf("Добавочный", "Перезапись")
                                .map {
                                    BaseButton(
                                        0, 0, 60.pc.w().value, 20, it.mcText,
                                        {
                                            layerText = it
                                            Minecraft.getInstance().setScreen(this@PlayAnimationScreen)
                                        },
                                        "hollowengine:textures/gui/long_button.png".rl
                                    )
                                }
                        ).open()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )

                +LabelWidget(
                    "Выберите режим проигрывания анимации:".mcText,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.1f
                )

                playMode = +BaseButton(
                    0, 0, 20.pc.w().value, 20, playText.mcText,
                    {
                        ChoiceScreen("Выберите тип анимации:",
                            arrayListOf("Одиночный", "Цикл", "Посл. кадр", "Обратный")
                                .map {
                                    BaseButton(
                                        0, 0, 60.pc.w().value, 20, it.mcText,
                                        {
                                            playText = it
                                            Minecraft.getInstance().setScreen(this@PlayAnimationScreen)
                                        },
                                        "hollowengine:textures/gui/long_button.png".rl
                                    )
                                }
                        ).open()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )


                +LabelWidget(
                    "Введите скорость анимации:".mcText,
                    anchor = Anchor.START,
                    color = 0xFFFFFF,
                    hoveredColor = 0xFFFFFF,
                    scale = 1.1f
                )

                speed = +HollowTextFieldWidget(
                    font, 0, 0, 25.pc.w().value, 20,
                    "1.0".mcText, "hollowengine:textures/gui/text_field.png".rl
                )
                speed.value = "1.0"
                val pattern = Pattern.compile("-?\\d+(\\.)?(\\d+)?")

                speed.setFilter { text ->
                    pattern.matcher(text).matches() || text.isEmpty()
                }

                lineBreak()

                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.start".mcTranslate,
                    {
                        if (animText != "Анимации") {
                            val layerMode =
                                if (layerText == "Слой" || layerText == "Добавочный") LayerMode.ADD else LayerMode.OVERWRITE
                            val playMode = when (playText) {
                                "Одиночный" -> PlayMode.ONCE
                                "Цикл" -> PlayMode.LOOPED
                                "Посл. кадр" -> PlayMode.LAST_FRAME
                                "Обратный" -> PlayMode.REVERSED
                                else -> PlayMode.ONCE
                            }

                            PauseRecordingPacket(
                                true, RecordingContainer(
                                    animText, layerMode, playMode, Float.parseFloat(speed.value)
                                )
                            ).send()
                            Minecraft.getInstance().player!![AnimatedEntityCapability::class].layers += AnimationLayer(animText, layerMode, playMode, Float.parseFloat(speed.value))
                        } else PauseRecordingPacket(true, null).send()
                        RecordingDriver.enable = true
                        onClose()
                    },
                    "hollowengine:textures/gui/long_button.png".rl
                )
                +BaseButton(
                    0, 0, 43.pc.w().value, 20,
                    "hollowengine.cancel".mcTranslate,
                    { RecordingDriver.enable = true; onClose() },
                    "hollowengine:textures/gui/long_button.png".rl
                )
            }
        }
    }

    class ChoiceScreen<T : AbstractWidget>(val text: String, val widgets: List<T>) : HollowScreen() {
        override fun init() {
            super.init()

            box {
                alignElements = Alignment.CENTER
                spacing = 5.pc x 0.px
                renderer = { stack, x, y, w, h ->
                    fill(stack, x, y, x + w, y + h, 0x8800173D.toInt())
                    fill(stack, x, y, x + w, y + 2, 0xFF07BBDB.toInt())
                    fill(stack, x, y + h - 2, x + w, y + h, 0xFF07BBDB.toInt())
                    fill(stack, x, y, x + 2, y + h, 0xFF07BBDB.toInt())
                    fill(stack, x + w - 2, y, x + w, y + h, 0xFF07BBDB.toInt())
                }

                elements {
                    +LabelWidget(
                        text.mcText,
                        anchor = Anchor.START,
                        color = 0xFFFFFF,
                        hoveredColor = 0xFFFFFF,
                        scale = 1.1f
                    )

                    this@ChoiceScreen.widgets.forEach { +it }
                }
            }
        }
    }
}