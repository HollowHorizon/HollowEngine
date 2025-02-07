package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.Assets
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.TextCaretNavigation
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextArea

fun createTexture(path: String) = Texture2d {
    Assets.loadImage2d(path).getOrThrow()
}

val COMPLETE_CLASS by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_class.png") }
val COMPLETE_METHOD by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_method.png") }
val COMPLETE_PACKAGE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_package.png") }
val COMPLETE_UNKNOWN by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_unknown.png") }
val COMPLETE_VARIABLE by lazy { createTexture("hollowengine:textures/gui/icons/autocomplete_variable.png") }

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

            modifier.margin(sizes.smallGap * 0.25f)
                .onEnter { isHovered = true }.onExit { isHovered = false }
                .onClick { use(textArea) }

            if (isHovered || isFocused) {
                modifier.background(
                    RoundRectBackground(
                        if (isHovered) colors.hoverBg.mulRgb(1.5f) else colors.hoverBg,
                        sizes.smallGap
                    )
                )
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
                ).alignY(AlignmentY.Center).size(sizes.gap, sizes.gap).margin(horizontal = sizes.smallGap)
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

        // Trigger code analysis
        modifier.onCharTyped(
            KeyEvent(
                UniversalKeyCode(0, null),
                LocalKeyCode(0, null),
                KeyboardInput.KEY_EV_CHAR_TYPED,
                0
            )
        )
    }

    enum class Icon {
        PACKAGE, CLASS, METHOD, VARIABLE, UNKNOWN
    }
}

class OnCompletionsEvent(val fileName: String, val completions: List<CompletionVariant>, val hashCode: Int) : Event