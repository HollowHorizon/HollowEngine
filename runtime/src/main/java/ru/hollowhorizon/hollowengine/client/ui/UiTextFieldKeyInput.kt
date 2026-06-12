package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

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

internal data object TextFieldDefaultKeyInputModifier : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        val input = style.input ?: UiInputStyle()
        style.input = input.copy(focusable = true, hoverable = true)
    }
}

internal fun TextFieldNode.handleDefaultTextFieldKeyInput(input: UiKeyInput): Boolean {
    if (completionItems.isNotEmpty()) {
        val completionChanged = when (input.key) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> acceptCompletion()
            GLFW.GLFW_KEY_ESCAPE -> closeCompletions()
            else -> false
        }
        if (completionChanged) input.markChanged()
        if (completionChanged || input.key == GLFW.GLFW_KEY_ESCAPE) return completionChanged
    }
    val changed = when (input.key) {
        GLFW.GLFW_KEY_BACKSPACE -> backspace(word = input.control)
        GLFW.GLFW_KEY_DELETE -> deleteForward(word = input.control)
        GLFW.GLFW_KEY_A -> {
            if (!input.command) return false
            selectAll()
            true
        }
        GLFW.GLFW_KEY_C -> {
            if (!input.command) return false
            selectedText()?.let { Minecraft.getInstance().keyboardHandler.setClipboard(it) }
            return true
        }
        GLFW.GLFW_KEY_X -> {
            if (!input.command) return false
            val selected = selectedText() ?: return true
            Minecraft.getInstance().keyboardHandler.setClipboard(selected)
            backspace()
        }
        GLFW.GLFW_KEY_V -> {
            if (!input.command) return false
            val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
            clipboard.isNotEmpty() && insert(clipboard)
        }
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
            if (input.control || !multiline) {
                moveCaret(0, input.shift)
            } else {
                moveCarets({ lineStart(value, it.position) }, input.shift)
            }
            true
        }
        GLFW.GLFW_KEY_END -> {
            if (input.control || !multiline) {
                moveCaret(value.length, input.shift)
            } else {
                moveCarets({ lineEnd(value, it.position) }, input.shift)
            }
            true
        }
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            if (input.alt) {
                openCompletions()
            } else {
                multiline && insert("\n")
            }
        }
        else -> false
    }
    if (changed) input.markChanged()
    return changed
}

private fun TextFieldNode.moveTextFieldVertically(input: UiKeyInput, lineDelta: Int): Boolean {
    val frame = input.frame ?: return false
    val layout = frame.layout[this]
    val style = frame.resolved[this]
    val textLayout = textFieldEditLayout(this, style, layout)
    moveCarets({ range ->
        textLayout.verticalCaretIndex(range.position, lineDelta, style.fontSize, style.fontFamily)
    }, input.shift)
    return true
}

private fun visibleTextFieldLines(input: UiKeyInput): Int {
    val field = input.node as? TextFieldNode ?: return 1
    val frame = input.frame ?: return 1
    val layout = frame.layout[field]
    val style = frame.resolved[field]
    val lineHeight = (style.fontSize + style.lineSpacing).coerceAtLeast(1f)
    return (layout.content.height / lineHeight).toInt().coerceAtLeast(1)
}

private fun wordLeft(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    while (index > 0 && text[index - 1].isWhitespace()) index--
    while (index > 0 && text[index - 1].isEditorWordChar()) index--
    if (index == position.coerceIn(0, text.length) && index > 0) index--
    return index
}

private fun wordRight(text: String, position: Int): Int {
    var index = position.coerceIn(0, text.length)
    while (index < text.length && text[index].isWhitespace()) index++
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
