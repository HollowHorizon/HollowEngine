package ru.hollowhorizon.hollowengine.common.files

import java.io.File
import java.nio.file.Path

object DirectoryManager {
    val HOLLOW_ENGINE: Path by lazy {
        File("").resolve("hollowengine").apply {
            if (!exists()) mkdirs()
        }.toPath()
    }
}