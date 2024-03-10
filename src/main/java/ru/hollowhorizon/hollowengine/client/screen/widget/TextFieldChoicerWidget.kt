package ru.hollowhorizon.hollowengine.client.screen.widget

import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.screens.widget.layout.box
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl

class TextFieldChoicerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val text: String,
    val consumer: (String) -> Unit,
) :
    HollowWidget(x, y, width, height, "".mcText) {
    var currentText: String = ""

    override fun init() {
        this.widgets.clear()

        box {
            size = 100.pc x 100.pc

            elements {
                val widget = +HollowTextFieldWidget(
                    font,
                    0,
                    0,
                    100.pc.w().value,
                    100.pc.h().value,
                    text.mcText,
                    "hollowengine:textures/gui/text_field.png".rl
                )
                widget.setResponder {
                    this@TextFieldChoicerWidget.currentText = it
                    consumer(it)
                }
            }
        }

    }

    fun search(query: String, search: String): Boolean {
        //for example query "st do" will return "stone_door"
        val queryWords = query.split("\\s+".toRegex())
        for (word in queryWords) {
            if (!search.contains(word)) return false
        }
        return true
    }
}