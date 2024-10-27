package ru.hollowhorizon.hollowengine.common.files

import java.io.File
import java.nio.file.Path
import kotlin.io.path.walk

object DirectoryManager {
    val HOLLOW_ENGINE: Path by lazy {
        File("").resolve("hollowengine").apply {
            if (!exists()) mkdirs()
        }.toPath()
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        val path = this.toPath()
        return HOLLOW_ENGINE.relativize(path).toString().replace("\\", "/")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return HOLLOW_ENGINE.resolve(this).toFile()
    }

    val npcScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".npc.kts") }
    val eventScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".event.kts") }
}