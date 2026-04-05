package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag

object CompletionRenderer {
    context(scope: UiScope)
    fun renderCompletion(
        completion: CompletionItem,
        isSelected: Boolean,
        typedPrefix: String,
        onClick: (CompletionItem) -> Unit,
        font: MsdfFont = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f),
    ): Unit = with(scope) {
        Row(Grow.Std) {
            val isHovered by modifier.hoverable()
            val hoverColor by animateColorAsState(if(!isHovered) EditorTheme.Popup.bg else EditorTheme.Popup.selectedBg, tween(0.1f, easing = Easing.quadRev))

            val backgroundColor by animateColorAsState(if (isSelected) EditorTheme.Popup.selectedBg else hoverColor, tween(0.1f, easing = Easing.quadRev))
            modifier
                .background(RectBackground(backgroundColor))
                .onClick { onClick(completion) }
                .padding(Dimensions.PaddingNormal) // Больше воздуха

            renderTag(completion.tag)

            Row {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(start = Dimensions.PaddingMedium)

                val fullText = completion.show + ((completion as? CompletionItem.Declaration)?.middle ?: "")
                val lowerFull = fullText.lowercase()
                val lowerPrefix = typedPrefix.lowercase()

                if (typedPrefix.isNotEmpty() && lowerFull.startsWith(lowerPrefix)) {
                    Text(fullText.substring(0, typedPrefix.length)) {
                        modifier.textColor(EditorTheme.Popup.textMatch).font(font)
                    }
                    Text(fullText.substring(typedPrefix.length)) {
                        modifier.textColor(EditorTheme.Popup.textPrimary).font(font)
                    }
                } else {
                    Text(fullText) {
                        modifier.textColor(EditorTheme.Popup.textPrimary).font(font)
                    }
                }
            }

            if (completion is CompletionItem.Declaration && !completion.tail.isNullOrBlank()) {
                Box(Grow.Std) { } // Spacer
                Text(completion.tail) {
                    modifier
                        .align(AlignmentX.End, AlignmentY.Center)
                        .margin(start = Dimensions.PaddingMedium)
                        .textColor(EditorTheme.Popup.textDim)
                        .font(font.derive(font.sizePts * 0.875f)) // Чуть меньше шрифт
                }
            }
        }
    }

    private fun UiScope.renderTag(tag: CompletionItemTag) {
        val (iconName, color) = when (tag) {
            CompletionItemTag.FUNCTION -> "method" to EditorTheme.Popup.Tag.function
            CompletionItemTag.PROPERTY -> "variable" to EditorTheme.Popup.Tag.property
            CompletionItemTag.CLASS -> "class" to EditorTheme.Popup.Tag.type
            CompletionItemTag.LOCAL_VARIABLE -> "variable" to EditorTheme.Popup.Tag.localVariable
            CompletionItemTag.KEYWORD -> "package" to EditorTheme.Popup.Tag.keyword
        }

        val imagePath = "hollowengine:textures/gui/icons/autocomplete_$iconName.svg"

        Image(imagePath) {
            modifier.size(16.dp, 16.dp).alignY(AlignmentY.Center).tint(color)
        }
    }
}