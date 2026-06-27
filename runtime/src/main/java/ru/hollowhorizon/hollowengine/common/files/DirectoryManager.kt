package ru.hollowhorizon.hollowengine.common.files

import java.io.File
import java.nio.file.Path

object DirectoryManager {
    val HOLLOW_ENGINE: Path by lazy {
        File("").resolve("hollowengine").apply {
            if (!exists()) mkdirs()
        }.toPath()
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        return toPath().toReadablePath()
    }

    @JvmStatic
    fun Path.toReadablePath(): String {
        return HOLLOW_ENGINE.relativize(this).toString().replace("\\", "/")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return HOLLOW_ENGINE.resolve(this).toFile()
    }

    val scripts: Sequence<File>
        get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".kts") }

    val componentScripts: Sequence<File> get() = scripts.filter { it.name.endsWith(".node.kts") }
}