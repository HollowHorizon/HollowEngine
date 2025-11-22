package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag

object CompletionRenderer {
    context(scope: UiScope)
    fun renderCompletion(completion: CompletionItem, textArea: TextAreaNode, isFocused: Boolean): Unit = with(scope) {
        Row(Grow.Std) {
            val (bgColor, iconBg, iconTint) = hoverColors(
                0.5f,
                listOf(Color("1B1E23FF"), Color("394450FF"), Color("6DA1E2FF")),
                listOf(Color("252930FF"), Color("586D84FF"), Color("98C6FFFF"))
            )

            if (isFocused) {
                bgColor.set(Color("252930FF"))
                iconBg.set(Color("586D84FF"))
                iconTint.set(Color("98C6FFFF"))
            }

            //modifier.onClick { use(textArea) }
            modifier.background(RectBackground(bgColor))

            renderTag(completion.tag, iconBg, iconTint)

            // Текст с подсветкой совпадений
            Row {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(horizontal = sizes.smallGap)


                //Text(completion.name.substringBefore(completion.insert)) { modifier.textColor(Color("98C6FFFF")) }
                Text(completion.insert) {  }

            }

//            Box {
//                modifier.width(Grow.Std).alignY(AlignmentY.Center)
//
//                Text(tail) {
//                    modifier.align(AlignmentX.End, AlignmentY.Center)
//                        .margin(horizontal = sizes.smallGap)
//                        .textColor(Color.LIGHT_GRAY)
//                }
//            }
        }
    }

    private fun UiScope.renderTag(tag: CompletionItemTag, iconBg: Color, iconTint: Color) {
        val code = when(tag) {
            CompletionItemTag.FUNCTION -> "method"
            CompletionItemTag.PROPERTY -> "variable"
            CompletionItemTag.CLASS -> "class"
            CompletionItemTag.LOCAL_VARIABLE -> "variable"
            CompletionItemTag.KEYWORD -> "package"
        }
        val image = "hollowengine:textures/gui/icons/autocomplete_${code}.svg"
        Image(image) {
            modifier.alignY(AlignmentY.Center)
                .size(24.dp + sizes.smallGap, 24.dp + sizes.smallGap).padding(sizes.smallGap)
                .background(RectBackground(iconBg))
                .tint(iconTint)
        }
    }
}