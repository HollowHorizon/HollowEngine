package ru.hollowhorizon.hollowengine.client.screen.imgui

import com.mojang.blaze3d.matrix.MatrixStack
import imgui.ImGui
import imgui.type.ImString
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.client.screen.IMGUIHandler

class IMGUIWidget(x: Int, y: Int, w: Int, h: Int) : HollowWidget(x, y, w, h, "".toSTC()) {
    val STRING = ImString()
    override fun renderButton(stack: MatrixStack, mouseX: Int, mouseY: Int, ticks: Float) =
        IMGUIHandler.render(x, y, width, height) {
            begin("Window") {

                sameLine(
                    {
                        button("BUTTON") { println("КЫНОПКА нажата") }
                    },
                    {
                        button("КЫНОПКА2") { println("КЫНОПКА2 нажата") }
                    },
                    {
                        checkbox("ЧИКБОКС") { println("ЧИКБОКС тык: $it") }
                    },
                    {
                        checkbox("ЧИКБОКС2") { println("ЧИКБОКС2 тык: $it") }
                    }
                )
                slider("CЛУЙДЕР", 0F, 1F) { println("CЛУЙДЕР тык: $it") }
                combo("К-К-КОМБО", arrayOf("К-К-КОМБО1", "К-К-КОМБО2", "К-К-КОМБО3")) { println("К-К-КОМБО тык: $it") }

                sameLine(
                    {
                        child("test", 250f, 100f, true) {
                            button("КЫНОПКА3") { println("КЫНОПКА3 нажата") }
                            button("КЫНОПКА4") { println("КЫНОПКА4 нажата") }
                            button("КЫНОПКА5") { println("КЫНОПКА5 нажата") }
                        }
                    },
                    {
                        child("test_child", 250F, 100F, true) {
                            button("КЫНОПКА6") { println("КЫНОПКА6 нажата") }
                            button("КЫНОПКА7") { println("КЫНОПКА7 нажата") }
                            button("КЫНОПКА8") { println("КЫНОПКА8 нажата") }
                        }
                    }
                )

                listBox("Список Какого-то Говна", arrayOf("Хрень1", "Хрень2", "Хрень3", "Хрень1", "Хрень2", "Хрень3", "Хрень1", "Хрень2", "Хрень3")) {
                    println("Список Какого-то Говна тык: $it")
                }

                ImGui.inputText("Введите какую-то дичь: ", STRING)

            }
        }


}