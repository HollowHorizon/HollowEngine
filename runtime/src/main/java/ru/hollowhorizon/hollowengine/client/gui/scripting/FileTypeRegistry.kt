package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile

object FileTypeRegistry {
    private val fileTypes = mutableListOf<FileType>()
    private var initialized = false

    fun register(
        extensions: List<String>,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null,
        priority: Int = extensions.firstOrNull()?.length ?: 0,
    ): FileType {
        val fileType = FileType(extensions, factory, fallback, priority)

        val insertIndex = fileTypes.indexOfFirst { it.priority < fileType.priority }
        if (insertIndex >= 0) {
            fileTypes.add(insertIndex, fileType)
        } else {
            fileTypes.add(fileType)
        }

        return fileType
    }

    fun registerExtension(
        extension: String,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null,
    ): FileType {
        return register(listOf(extension), factory, fallback)
    }

    fun registerExtensions(
        extensions: List<String>,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null,
    ): FileType {
        return register(extensions, factory, fallback)
    }

    fun registerSuffix(
        suffix: String,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null,
    ): FileType {
        return register(listOf(suffix), factory, fallback, priority = suffix.length * 2)
    }

    fun initialize() {
        if (initialized) return
        initialized = true

        val event = RegisterFileTypeEvent()
        RegisterFileTypeEvent.post(event)

        event.getTypes().forEach { type ->
            val insertIndex = fileTypes.indexOfFirst { it.priority < type.priority }
            if (insertIndex >= 0) {
                fileTypes.add(insertIndex, type)
            } else {
                fileTypes.add(type)
            }
        }
    }

    fun findType(path: String): FileType? {
        return fileTypes.firstOrNull { it.matches(path) }
    }

    fun getAllTypes(): List<FileType> = fileTypes.toList()
}
