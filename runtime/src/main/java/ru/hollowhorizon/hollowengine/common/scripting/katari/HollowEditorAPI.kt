package ru.hollowhorizon.hollowengine.common.scripting.katari

import org.slf4j.LoggerFactory
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager

object HollowEditorAPI {
    private val LOGGER = LoggerFactory.getLogger("HollowEditorAPI")

    fun generate() {
        val outputFile = DirectoryManager.HOLLOW_ENGINE
            .resolve("node-editor")
            .resolve("bindings.json")
            .toFile()

        if (outputFile.exists()) return

        val json = HollowEditorAPI::class.java
            .getResourceAsStream("/katari-bindings.json")
            ?.readBytes()
            ?.decodeToString()
            ?: run {
                LOGGER.error("katari-bindings.json not found in jar")
                return
            }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(json)
        LOGGER.info("Node editor bindings saved → ${outputFile.absolutePath}")
    }
}