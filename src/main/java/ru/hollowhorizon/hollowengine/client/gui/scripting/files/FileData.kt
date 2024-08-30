package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.type.ImBoolean

abstract class FileData(
    val name: String,
    val path: String,
    val open: ImBoolean,
) {
    abstract fun draw()

    abstract fun save()
}