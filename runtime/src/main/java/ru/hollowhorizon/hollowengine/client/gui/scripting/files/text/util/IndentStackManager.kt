package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

class IndentStackManager {
    private val stack = mutableListOf<Int>()

    fun update(indentIndex: Int, lineLength: Int) {
        if (indentIndex == -1 && lineLength != 0) return

        while (indentIndex <= (stack.lastOrNull() ?: 0) && stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (lineLength == 0) break
        }

        if (indentIndex > 0 && indentIndex != stack.lastOrNull()) {
            stack.add(indentIndex)
        }
    }

    fun popToIndent(indentIndex: Int, lineLength: Int) {
        if (indentIndex == -1 && lineLength != 0) return

        while (indentIndex <= (stack.lastOrNull() ?: 0) && stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (lineLength == 0) break
        }
    }

    fun pushIndent(indentIndex: Int) {
        if (indentIndex > 0 && indentIndex != stack.lastOrNull()) {
            stack.add(indentIndex)
        }
    }

    fun getIndents(): IntArray = stack.toIntArray()

    val indentLevel: Int get() = stack.size
}