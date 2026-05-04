package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.EditorFile
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class RegisterFileTypeEvent : ClientEvent {
    private val registry = mutableListOf<FileType>()

    fun register(
        extension: String,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null
    ) {
        registry.add(FileType(listOf(extension), factory, fallback))
    }

    fun register(
        extensions: List<String>,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null
    ) {
        registry.add(FileType(extensions, factory, fallback))
    }

    fun registerSuffix(
        suffix: String,
        factory: (String, ByteArray) -> EditorFile,
        fallback: FileType? = null
    ) {
        registry.add(FileType(listOf(suffix), factory, fallback, priority = suffix.length * 2))
    }
    
    internal fun getTypes(): List<FileType> = registry.toList()

    companion object: EventHandler<RegisterFileTypeEvent>()
}
