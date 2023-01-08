package ru.hollowhorizon.hollowstory.client.gui

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.widget.box
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.gui.widget.ModelPreviewWidget
import ru.hollowhorizon.hollowstory.client.gui.widget.box.TypeBox
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings

class NPCBuilderScreen : HollowScreen("".toSTC()) {
    val npc: NPCEntity = NPCEntity(NPCSettings(), Minecraft.getInstance().level)

    override fun init() {
        super.init()
        box {
            alignment = Alignment.CENTER
            size = 90f x 85f
            offset = 0 x 5f

            box {
                alignment = Alignment.RIGHT_CENTER
                size = 50f x 100f

                widgets { x, y, w, h ->
                    add(ModelPreviewWidget(x, y, w, h, this.width, this.height))
                }
            }

            box {
                alignment = Alignment.LEFT_CENTER
                size = 50f x 100f

                box {
                    alignment = Alignment.TOP_CENTER
                    size = 100f x 20f

                    widgets { x, y, w, h ->
                        add(
                            TypeBox(
                                x, y, w, h, "Введите путь к модели:", "модель (modid:path_to_model)"
                            )
                        )
                    }
                }

                box {
                    alignment = Alignment.TOP_CENTER
                    size = 100f x 20f
                    offset = 0 x 20f

                    widgets { x, y, w, h ->
                        add(
                            TypeBox(
                                x, y, w, h, "Введите путь к текстуре:", "текстура (modid:path_to_texture)"
                            )
                        )
                    }
                }

                box {
                    alignment = Alignment.TOP_CENTER
                    size = 100f x 20f
                    offset = 0 x 40f

                    widgets { x, y, w, h ->
                        add(
                            TypeBox(
                                x, y, w, h, "Введите путь к файлу анимаций:", "анимация (modid:path_to_animation)"
                            )
                        )
                    }
                }
            }
        }
    }


}