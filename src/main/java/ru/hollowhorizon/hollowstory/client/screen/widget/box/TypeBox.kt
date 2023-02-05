package ru.hollowhorizon.hollowstory.client.screen.widget.box

import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.screen.widget.TextFieldChoicerWidget

class TypeBox(
    x: Int, y: Int, width: Int, height: Int,
    val text: String = "Введите путь к модели:",
    val textFieldText: String = "модель (modid:path_to_file)",
    val onValueChange: (TypeBox) -> Unit = {},
) : HollowWidget(x, y, width, height, "".toSTC()) {
    var currentText: String = ""

    override fun init() {
        super.init()
        box {
            box {
                align = Alignment.TOP_CENTER
                size = 100.pc x 30.pc
                elements {
                    +TextBox(0, 0, 100.pc.w().value, 100.pc.h().value, this@TypeBox.text.toSTC())
                }
            }

            box {
                align = Alignment.BOTTOM_CENTER
                size = 90.pc x 50.pc
                elements {
                    +TextFieldChoicerWidget(
                        0, 0, 100.pc.w().value, 100.pc.h().value, this@TypeBox.textFieldText
                    ) { text ->
                        this@TypeBox.currentText = text
                        onValueChange(this@TypeBox)
                    }

                }
            }
        }
    }
}