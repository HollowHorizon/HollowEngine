package ru.hollowhorizon.hollowengine.client.screen

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.client.screen.widget.ModelPreviewWidget
import ru.hollowhorizon.hollowengine.client.screen.widget.box.TypeBox
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCBuilderScreen : HollowScreen("".toSTC()) {
    val npc: NPCEntity = NPCEntity(Minecraft.getInstance().level!!, "")

    override fun init() {
        super.init()
        box {
            align = Alignment.CENTER
            size = 100.pc x 100.pc
            pos = 0.px x 5.pc

            box {
                align = Alignment.RIGHT_CENTER
                size = 50.pc x 100.pc

                elements {
                    +ModelPreviewWidget(0, 0, 100.pc.w().value, 100.pc.h().value, this@NPCBuilderScreen.width, this@NPCBuilderScreen.height)
                }
            }

            box {
                align = Alignment.LEFT_CENTER
                size = 50.pc x 100.pc

                box {
                    align = Alignment.TOP_CENTER
                    size = 100.pc x 20.pc

                    elements {
                        +TypeBox(
                            0, 0, 100.pc.w().value, 100.pc.h().value,
                            "Введите путь к модели:",
                            "модель (modid:path/to/model.gltf)"
                        )
                    }
                }

                box {
                    align = Alignment.TOP_CENTER
                    size = 100.pc x 20.pc
                    pos = 0.px x 20.pc

                    elements {
                        +TypeBox(
                            0, 0, 100.pc.w().value, 100.pc.h().value,
                            "Введите путь к модели:",
                            "модель (modid:path/to/model.gltf)"
                        )
                    }
                }

                box {
                    align = Alignment.TOP_CENTER
                    size = 100.pc x 20.pc
                    pos = 0.pc x 40.pc

                    elements {
                        +TypeBox(
                            0, 0, 100.pc.w().value, 100.pc.h().value,
                            "Введите путь к модели:",
                            "модель (modid:path/to/model.gltf)"
                        )
                    }
                }
            }
        }
    }


}