package ru.hollowhorizon.hollowengine.client.ui.widgets

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.inlineWidgetMetrics
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.verticalCaretIndex

data class UiKeyInput(
    val event: UiEvent,
) {
    val node: UiNode get() = event.node
    val frame: HollowUiFrame? get() = event.frame
    val key: Int get() = event.key
    val scanCode: Int get() = event.scanCode
    val modifiers: Int get() = event.modifiers
    val shift: Boolean get() = modifiers and GLFW.GLFW_MOD_SHIFT != 0
    val control: Boolean get() = modifiers and GLFW.GLFW_MOD_CONTROL != 0
    val alt: Boolean get() = modifiers and GLFW.GLFW_MOD_ALT != 0
    val command: Boolean get() = control || modifiers and GLFW.GLFW_MOD_SUPER != 0

    fun markChanged() {
        event.markChanged()
    }
}

internal data object TextFieldDefaultKeyInputModifier : InputModifierNode, UiModifierPatchNode {
    override fun applyPatch(style: UiStylePatch) {
        val input = style.input ?: UiInputStyle()
        style.input = input.copy(focusable = true, hoverable = true)
    }
}

internal fun TextFieldNode.handleDefaultTextFieldKeyInput(input: UiKeyInput): Boolean {
    if (completionItems.isNotEmpty() && handleCompletionKeyInput(input)) {
        return true
    }

    if (input.command && handleClipboardAndSelection(input)) {
        input.markChanged()
        return true
    }

    val changed = handleNavigationAndEditing(input)
    if (changed) {
        input.markChanged()
    }
    return changed
}


private fun TextFieldNode.handleCompletionKeyInput(input: UiKeyInput): Boolean {
    val completionChanged = when (input.key) {
        GLFW.GLFW_KEY_UP -> moveCompletionSelection(-1)
        GLFW.GLFW_KEY_DOWN -> moveCompletionSelection(1)
        GLFW.GLFW_KEY_TAB,
        GLFW.GLFW_KEY_ENTER,
        GLFW.GLFW_KEY_KP_ENTER -> acceptCompletion(completionSelectedIndex)
        GLFW.GLFW_KEY_ESCAPE -> closeCompletions()
        else -> false
    }

    if (completionChanged) {
        input.markChanged()
    }

    if (completionChanged || input.key in listOf(GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ESCAPE)) {
        return true
    }

    if (input.key == GLFW.GLFW_KEY_LEFT || input.key == GLFW.GLFW_KEY_RIGHT) {
        closeCompletions()
        input.markChanged()
    }

    return false
}

private fun TextFieldNode.handleClipboardAndSelection(input: UiKeyInput): Boolean {
    return when (input.key) {
        GLFW.GLFW_KEY_A -> { selectAll(); true }
        GLFW.GLFW_KEY_C -> { selectedText()?.let { Minecraft.getInstance().keyboardHandler.clipboard = it }; true }
        GLFW.GLFW_KEY_X -> {
            val selected = selectedText() ?: return true
            Minecraft.getInstance().keyboardHandler.clipboard = selected
            backspace()
        }
        GLFW.GLFW_KEY_V -> Minecraft.getInstance().keyboardHandler.clipboard.let { it.isNotEmpty() && insert(it) }
        GLFW.GLFW_KEY_Z -> if (input.shift) redo() else undo()
        GLFW.GLFW_KEY_Y -> redo()
        else -> false
    }
}

private fun TextFieldNode.handleNavigationAndEditing(input: UiKeyInput): Boolean {
    return when (input.key) {
        GLFW.GLFW_KEY_BACKSPACE -> backspace(word = input.control)
        GLFW.GLFW_KEY_DELETE -> deleteForward(word = input.control)

        GLFW.GLFW_KEY_LEFT -> {
            moveCarets({ range ->
                if (input.control) wordLeft(value, range.position) else range.position - 1
            }, input.shift)
            true
        }
        GLFW.GLFW_KEY_RIGHT -> {
            moveCarets({ range ->
                if (input.control) wordRight(value, range.position) else range.position + 1
            }, input.shift)
            true
        }

        GLFW.GLFW_KEY_UP -> multiline && moveTextFieldVertically(input, -1)
        GLFW.GLFW_KEY_DOWN -> multiline && moveTextFieldVertically(input, 1)
        GLFW.GLFW_KEY_PAGE_UP -> multiline && moveTextFieldVertically(input, -visibleTextFieldLines(input))
        GLFW.GLFW_KEY_PAGE_DOWN -> multiline && moveTextFieldVertically(input, visibleTextFieldLines(input))

        GLFW.GLFW_KEY_HOME -> {
            if (input.control || !multiline) moveCaret(0, input.shift)
            else moveCarets({ lineStart(value, it.position) }, input.shift)
            true
        }
        GLFW.GLFW_KEY_END -> {
            if (input.control || !multiline) moveCaret(value.length, input.shift)
            else moveCarets({ lineEnd(value, it.position) }, input.shift)
            true
        }

        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            if (input.alt) {
                openCompletions()
                true
            } else {
                insertNewlineWithIndent()
            }
        }
        GLFW.GLFW_KEY_TAB -> {
            if (!multiline || indentSize == null || input.control || input.alt) {
                false
            } else if (input.shift) {
                unindent()
            } else {
                indent()
            }
        }
        else -> false
    }
}

private fun TextFieldNode.moveTextFieldVertically(input: UiKeyInput, lineDelta: Int): Boolean {
    val frame = input.frame ?: return false
    val layout = frame.layout[this]
    val style = this.resolvedSnapshot
    val fontSize = style.fontSize
    val textLayout = textFieldEditLayout(this, style, layout, layout.inlineWidgetMetrics())
    moveCarets({ range ->
        textLayout.verticalCaretIndex(range.position, lineDelta, fontSize, style.fontFamily)
    }, input.shift)
    return true
}

private fun visibleTextFieldLines(input: UiKeyInput): Int {
    val field = input.node as? TextFieldNode ?: return 1
    val frame = input.frame ?: return 1
    val layout = frame.layout[field]
    val style = field.resolvedSnapshot
    val lineHeight = (style.fontSize + style.lineSpacing).coerceAtLeast(1f)
    return (layout.content.height / lineHeight).toInt().coerceAtLeast(1)
}


internal fun wordLeft(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    if (index > 0 && text[index - 1] == '\n') return index - 1
    while (index > 0 && text[index - 1].isWhitespace() && text[index - 1] != '\n') index--
    while (index > 0 && text[index - 1].isEditorWordChar()) index--
    if (index == position.coerceIn(0, text.length) && index > 0) index--
    return index
}

internal fun wordRight(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    if (index < text.length && text[index] == '\n') return index + 1
    while (index < text.length && text[index].isWhitespace() && text[index] != '\n') index++
    if (index < text.length && text[index] == '\n') return index + 1
    while (index < text.length && text[index].isEditorWordChar()) index++
    if (index == position.coerceIn(0, text.length) && index < text.length) index++
    return index
}

private fun lineStart(text: String, position: Int): Int {
    if (position <= 0) return 0
    return text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
}

private fun lineEnd(text: String, position: Int): Int {
    val nextNewline = text.indexOf('\n', position.coerceIn(0, text.length))
    return if (nextNewline < 0) text.length else nextNewline
}

private fun Char.isEditorWordChar(): Boolean = this == '_' || isLetterOrDigit()
