package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import imgui.type.ImBoolean
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2

abstract class FileData(
    val project: IDEGuiV2,
    val fileName: String,
    val filePath: String,
    val isOpen: ImBoolean,
) {
    abstract fun draw()

    abstract fun save()
    open fun destroy() {}
}