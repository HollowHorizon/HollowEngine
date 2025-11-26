package ru.hollowhorizon.hollowengine.common.files

import java.io.File
import java.nio.file.Path

object DirectoryManager {
    val HOLLOW_ENGINE: Path by lazy {
        File("").resolve("hollowengine").apply {
            if (!exists()) mkdirs()
        }.toPath()
    }

    init {
//        DirectoryWatcher(HOLLOW_ENGINE.resolve("scripts")) { path, event ->
//            when(event) {
//                ENTRY_CREATE -> {
//                    if(path.fileName.toString().endsWith(".event.kts")) eventScripts.add(path)
//                }
//                ENTRY_DELETE -> {
//                    if(path.fileName.toString().endsWith(".event.kts")) eventScripts.remove(path)
//                }
//            }
//        }.start()
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

}