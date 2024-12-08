package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.editor.ui.lineHeight
import de.fabmax.kool.input.*
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.TextCaretNavigation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.scripting.createTexture
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ScriptTextArea

val COMPLETE_CLASS by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_class.png".rl, 16, 16) }
val COMPLETE_METHOD by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_method.png".rl, 16, 16) }
val COMPLETE_PACKAGE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_package.png".rl, 16, 16) }
val COMPLETE_UNKNOWN by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_unknown.png".rl, 16, 16) }
val COMPLETE_VARIABLE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_variable.png".rl, 16, 16) }

data class CompletionVariant(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: Icon,
) {
    override fun toString() = displayText

    fun UiScope.create(textArea: ScriptTextArea, isFocused: Boolean) {
        Row(Grow.Std) {
            var isHovered by remember { mutableStateOf(false) }

            modifier.margin(sizes.smallGap)
                .onEnter { isHovered = true }.onExit { isHovered = false }
                .onClick { use(textArea) }

            if (isHovered || isFocused) {
                modifier.background(RoundRectBackground(if(isHovered) colors.hoverBg.mulRgb(1.5f) else colors.hoverBg, sizes.gap))
            }

            Image {
                modifier.image(
                    when (icon) {
                        Icon.PACKAGE -> COMPLETE_PACKAGE
                        Icon.METHOD -> COMPLETE_METHOD
                        Icon.VARIABLE -> COMPLETE_VARIABLE
                        Icon.CLASS -> COMPLETE_CLASS
                        Icon.UNKNOWN -> COMPLETE_UNKNOWN
                    }
                ).alignY(AlignmentY.Center).size(sizes.lineHeight, sizes.lineHeight).margin(horizontal = sizes.smallGap)
            }
            Text(displayText) {
                modifier.align(AlignmentX.Start, AlignmentY.Center).margin(horizontal = sizes.smallGap)
            }

            Box {
                modifier.width(Grow.Std)

                Text(tail) {
                    modifier.align(AlignmentX.End, AlignmentY.Center)
                        .margin(horizontal = sizes.smallGap)
                        .textColor(Color.LIGHT_GRAY)
                }
            }
        }
    }

    fun use(scriptTextArea: ScriptTextArea) {
        val modifier = scriptTextArea.modifier
        val lineIndex = modifier.selectionStartLine.coerceAtMost(scriptTextArea.lineProvider.lastIndex)
        val line = scriptTextArea.lineProvider[lineIndex].text
        val startChar = modifier.selectionStartChar
        val startWord = TextCaretNavigation.startOfWord(line, startChar)
        val editor = modifier.editorHandler ?: return

        var text = text
        var offset = 0
        if(text.endsWith("(")) {
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
        modifier.onCharTyped(KeyEvent(UniversalKeyCode(0, null), LocalKeyCode(0, null), KeyboardInput.KEY_EV_CHAR_TYPED, 0))
    }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN
    }
}

class OnCompletionsEvent(val fileName: String, val completions: List<CompletionVariant>) : Event