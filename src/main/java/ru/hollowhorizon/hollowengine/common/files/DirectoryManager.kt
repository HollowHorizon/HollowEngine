package ru.hollowhorizon.hollowengine.common.files

import com.sun.nio.file.ExtendedWatchEventModifier
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import kotlin.concurrent.thread
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

object DirectoryManager {
    val HOLLOW_ENGINE: Path by lazy {
        File("").resolve("hollowengine").apply {
            if (!exists()) mkdirs()
        }.toPath()
    }
    val eventScripts: MutableSet<Path> = HOLLOW_ENGINE.resolve("scripts").walk()
        .filter { !it.isDirectory() }
        .filter { it.fileName.toString().endsWith(".event.kts") }
        .toSortedSet()

    init {
        DirectoryWatcher(HOLLOW_ENGINE.resolve("scripts")) { path, event ->
            when(event) {
                ENTRY_CREATE -> {
                    if(path.fileName.toString().endsWith(".event.kts")) eventScripts.add(path)
                }
                ENTRY_DELETE -> {
                    if(path.fileName.toString().endsWith(".event.kts")) eventScripts.remove(path)
                }
            }
        }.start()
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

    val guiCache = HOLLOW_ENGINE.resolve(".gui_cache").toFile().apply {
        if (!exists()) mkdirs()
    }
    val storyScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".scene.kts") }
    val guiScripts get() = HOLLOW_ENGINE.resolve("scripts").toFile().walk().filter { it.name.endsWith(".gui.kts") }
}