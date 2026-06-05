package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider

fun TextLineProvider.fullText(): String {
    return (0 until size).joinToString("\n") { index ->
        this[index].text
    }
}