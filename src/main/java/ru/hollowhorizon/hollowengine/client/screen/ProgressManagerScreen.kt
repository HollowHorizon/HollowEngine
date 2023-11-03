package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.screens.widget.layout.PlacementType
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.HollowEngine

class ProgressManagerScreen : HollowScreen("Progress Manager".toSTC()) {
    override fun init() {
        super.init()

        box {
            size = 100.pc x 100.pc

            elements {
                box {
                    size = 100.pc x 15.pc
                    align = Alignment.TOP_CENTER
                    renderer = { stack, x, y, w, h ->
                        bind(HollowEngine.MODID, "gui/event_list/event_list.png")
                        blit(stack, x, y, 0f, 0f, w, h, w, h)

                        font.drawScaled(
                            stack, Anchor.CENTER,
                            "Список Событий".toSTC(),
                            x + w / 2,
                            y + h / 2 + 1,
                            0xFFFFFF,
                            2.5f
                        )
                    }
                }
                box {
                    size = 100.pc x 85.pc
                    align = Alignment.BOTTOM_CENTER
                    alignElements = Alignment.TOP_LEFT
                    spacing = 0.px x 5.pc
                    placementType = PlacementType.VERTICAL

                    renderer = { stack, x, y, w, h ->
                        font.drawScaled(
                            stack, Anchor.CENTER,
                            "Заданий пока нету, возможно они появятся позже!".toSTC(),
                            x + w / 2,
                            y + h / 2,
                            0xFFFFFF,
                            1.0f
                        )
                    }

                    elements {

//                        manager.tasks().forEach { task ->
//                            +TaskWidget(task, 100.pc.w().value, 15.pc.h().value)
//                        }

                    }
                }
            }
        }
    }

    override fun render(pMatrixStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        renderBackground(pMatrixStack)
        super.render(pMatrixStack, pMouseX, pMouseY, pPartialTicks)
    }

    class TaskWidget(task: String, w: Int, h: Int) : HollowWidget(0, 0, w, h, task.toSTC()) {
        override fun init() {
            box {
                size = 100.pc x 100.pc

                renderer = { stack, x, y, w, h ->
                    RenderSystem.enableBlend()
                    bind(HollowEngine.MODID, "gui/event_list/event_list_value.png")
                    blit(stack, x, y, 0f, 0f, w, h, w, h)

                    val lines = font.split(message, w)
                    val center = (lines.size * font.lineHeight) / 2
                    lines.forEachIndexed { index, text ->
                        font.drawShadow(
                            stack,
                            text,
                            x.toFloat() + 6.pc.w().value,
                            (y + height / 2 - font.lineHeight / 2 - center * index + index * font.lineHeight).toFloat(),
                            0xFFFFFF
                        )
                    }
                }
            }
        }
    }
}