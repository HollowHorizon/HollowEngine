package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.TextCaretNavigation
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.setSelectionRange
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors

fun createTexture(path: String) = Texture2d {
    Assets.loadImage2d(path).getOrThrow()
}

val COMPLETE_CLASS by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_class.svg") }
val COMPLETE_METHOD by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_method.svg") }
val COMPLETE_PACKAGE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_package.svg") }
val COMPLETE_VARIABLE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_variable.svg") }

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
) {
    override fun toString() = displayText

    fun UiScope.create(textArea: TextAreaNode, isFocused: Boolean) {
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

            modifier.onClick { use(textArea) }

            modifier.background(RectBackground(bgColor))

            if (icon == Icon.UNKNOWN) {
                Box {
                    modifier.alignY(AlignmentY.Center)
                        .size(24.dp+sizes.smallGap, 24.dp+sizes.smallGap).padding(sizes.smallGap)
                        .background(RectBackground(iconBg))
                }
            } else {
                Image("hollowengine:textures/gui/icons/autocomplete_${icon.name.lowercase()}.svg") {
                    modifier.alignY(AlignmentY.Center)
                        .size(24.dp+sizes.smallGap, 24.dp+sizes.smallGap).padding(sizes.smallGap)
                        .background(RectBackground(iconBg))
                        .tint(iconTint)
                }
            }
            Text(displayText) {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(horizontal = sizes.smallGap)
            }

            Box {
                modifier.width(Grow.Std).alignY(AlignmentY.Center)

                Text(tail) {
                    modifier.align(AlignmentX.End, AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                        .textColor(Color.LIGHT_GRAY)
                }
            }
        }
    }

    fun use(scriptTextArea: TextAreaNode) {
        val modifier = scriptTextArea.modifier
        val lineIndex = modifier.selectionStartLine.coerceAtMost(scriptTextArea.lineProvider.lastIndex)
        val line = scriptTextArea.lineProvider[lineIndex].text
        val startChar = modifier.selectionStartChar
        val startWord = TextCaretNavigation.startOfWord(line, startChar)
        val editor = modifier.editorHandler ?: return

        var text = text
        var offset = 0
        if (text.endsWith("(")) {
            text = "$text)"
            offset = 1
        }

        editor.replaceText(
            modifier.selectionStartLine,
            modifier.selectionStartLine,
            startWord,
            startChar,
            text,
            scriptTextArea
        )
        modifier.setSelectionRange(
            modifier.selectionStartLine,
            modifier.selectionCaretLine,
            startWord + text.length - offset,
            startWord + text.length - offset
        )
        modifier.completions.clear()
        modifier.setCompletionIndex(0)
    }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN
    }
}

class OnCompletionsEvent(val fileName: String, val completions: List<CompletionVariant>, val hashCode: Int) : Event