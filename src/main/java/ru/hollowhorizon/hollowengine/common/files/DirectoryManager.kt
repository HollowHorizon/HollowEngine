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

    val guiCache = HOLLOW_ENGINE.resolve(".gui_cache").toFile().apply {
        if (!exists()) mkdirs()
    }
    val storyScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".scene.kts") }
    val eventScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".event.kts") }
    val guiScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".gui.kts") }
}