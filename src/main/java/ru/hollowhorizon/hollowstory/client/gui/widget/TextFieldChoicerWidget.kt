package ru.hollowhorizon.hollowstory.client.gui.widget

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.widget.Widget
import net.minecraft.util.ResourceLocation
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.screens.widget.box
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.screens.widget.list.ListWidget
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC

class TextFieldChoicerWidget(x: Int, y: Int, width: Int, height: Int, val text: String, val consumer: (String) -> Unit) :
    HollowWidget(x, y, width, height, "".toSTC()) {
    var currentText: String = ""

    override fun init() {
        this.widgets.clear()

        box {
            size = 100f x 100f

            widgets { x, y, w, h ->
                val widget = HollowTextFieldWidget(
                    font,
                    x,
                    y,
                    w,
                    h,
                    text.toSTC(),
                    "hollowstory:textures/gui/text_field.png".toRL()
                )
                widget.setResponder {
                    this@TextFieldChoicerWidget.currentText = it
                    consumer(it)
                }
                add(widget)
            }
        }

        if (ResourceLocation.isValidResourceLocation(this.currentText)) {
            box {
                offset = 0f x 100f
                size = 100f x 300f


                val models = Minecraft.getInstance().resourceManager.listResources("models") {
                    search(
                        this@TextFieldChoicerWidget.currentText,
                        it
                    )
                }
                
                val modelSuggestions = mutableListOf<Widget>()
                for (model in models) {
                    modelSuggestions.add(
                        BaseButton(
                            0,
                            0,
                            (size.width * 0.9f).toInt(),
                            (size.height * 0.9f / 3).toInt(),
                            model.toString().toSTC(),
                            {}, 
                            "hollowstory:textures/gui/text_field.png".toRL()
                        )
                    )
                }
                
                widgets { x, y, w, h ->
                    add(ListWidget(modelSuggestions, x, y, w, h))
                }
            }
        }
    }

    fun search(query: String, search: String): Boolean{
        //for example query "st do" will return "stone_door"
        val queryWords = query.split("\\s+".toRegex())
        for (word in queryWords) {
            if (!search.contains(word)) return false
        }
        return true
    }
}