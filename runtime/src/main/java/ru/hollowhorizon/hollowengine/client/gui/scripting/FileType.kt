package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile

class FileType(
    val extensions: List<String>,
    val factory: (String, ByteArray) -> EditorFile,
    val fallback: FileType? = null,
    val priority: Int = 0
) {
    fun matches(path: String): Boolean = extensions.any { path.endsWith(it, ignoreCase = true) }

    fun resolve(path: String, bytes: ByteArray): EditorFile? {
        return try {
            factory(path, bytes)
        } catch (e: Exception) {
            fallback?.resolve(path, bytes)
        }
    }
}
