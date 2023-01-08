package ru.hollowhorizon.hollowstory.client.gui.widget.box

import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.screens.widget.box
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.client.gui.widget.TextFieldChoicerWidget

class TypeBox(
    x: Int, y: Int, width: Int, height: Int,
    val text: String = "Введите путь к модели:",
    val textFieldText: String = "модель (modid:path_to_file)",
    val onValueChange: (TypeBox) -> Unit = {}
) : HollowWidget(x, y, width, height, "".toSTC()) {
    var currentText: String = ""

    override fun init() {
        super.init()
        box {
            box {
                alignment = Alignment.TOP_CENTER
                size = 100f x 30f
                widgets { x, y, w, h ->
                    add(TextBox(x, y, w, h, this@TypeBox.text.toSTC()))
                }
            }

            box {
                alignment = Alignment.BOTTOM_CENTER
                size = 90f x 50f
                widgets { x, y, w, h ->
                    add(
                        TextFieldChoicerWidget(
                            x, y, w, h, this@TypeBox.textFieldText
                        ) { text ->
                            this@TypeBox.currentText = text
                            onValueChange(this@TypeBox)
                        }
                    )
                }
            }
        }
    }
}