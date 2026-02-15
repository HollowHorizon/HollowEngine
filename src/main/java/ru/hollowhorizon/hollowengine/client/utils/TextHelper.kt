package ru.hollowhorizon.hollowengine.client.utils

fun offset(content: String, line: Int, char: Int): Int {
    if (content.isEmpty()) return 0

    val safeLine = line.coerceAtLeast(0)
    val safeChar = char.coerceAtLeast(0)

    var currentLine = 0
    var idx = 0

    while (idx < content.length && currentLine < safeLine) {
        if (content[idx] == '\n') currentLine++
        idx++
    }

    if (currentLine < safeLine) return content.length

    val target = idx + safeChar
    if (target > content.length) return content.length

    return target
}