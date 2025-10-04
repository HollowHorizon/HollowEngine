package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize

object AutoRuns {
    private val file = DirectoryManager.HOLLOW_ENGINE.resolve("autoruns.json").toFile()

    var content = Content()

    fun load() {
        if (!file.exists()) save()

        try {
            content = file.inputStream().let { JsonFormat.decodeFromStream(it) }
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Autoruns configuration structure changed. Resetting.")
        }
    }

    fun save() {
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        file.outputStream().let { JsonFormat.encodeToStream(JsonFormat.serialize(content), it) }
    }

    @Serializable
    class Content(
        val events: MutableSet<String> = hashSetOf(),
        val levelComponents: MutableMap<String, Boolean> = hashMapOf(),
    )
}

@SubscribeEvent
fun onServerStart(event: ServerEvent.Starting) {
    AutoRuns.load()
}

@SubscribeEvent
fun onServerStop(event: ServerEvent.Stoping) {
    AutoRuns.save()
}

