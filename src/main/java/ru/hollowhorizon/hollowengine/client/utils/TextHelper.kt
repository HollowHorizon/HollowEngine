package ru.hollowhorizon.hollowengine.client.utils

fun offset(content: String, line: Int, char: Int): Int {
    var currentLine = 0
    var idx = 0

    while (idx < content.length && currentLine < line) {
        if (content[idx] == '\n') currentLine++
        idx++
    }

    if (currentLine < line) error("Line $line out of range")

    val target = idx + char
    if (target > content.length) error("Char $char out of range in line $line")

    return target
}