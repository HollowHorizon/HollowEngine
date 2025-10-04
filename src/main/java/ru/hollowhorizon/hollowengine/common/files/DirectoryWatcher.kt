package ru.hollowhorizon.hollowengine.common.files

import kotlinx.io.IOException
import ru.hollowhorizon.hollowengine.HollowEngine
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

class DirectoryWatcher(private val directory: Path, private val callback: (Path, WatchEvent.Kind<*>) -> Unit) {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val keyMap: MutableMap<WatchKey, Path> = mutableMapOf()
    private var isRunning: Boolean = false

    init {
        require(Files.isDirectory(directory)) { "Указанный путь $directory не является директорией" }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        registerDirectories(directory)

        Thread {
            try {
                while (isRunning) {
                    val key = watchService.take() ?: continue
                    val dirPath = keyMap[key] ?: continue

                    key.pollEvents().forEach { event ->
                        val kind = event.kind()
                        val fileName = (event as WatchEvent<Path>).context()
                        val fullPath = dirPath.resolve(fileName)

                        callback(fullPath, kind)

                        if (kind == ENTRY_CREATE && Files.isDirectory(fullPath)) {
                            registerDirectories(fullPath)
                        }
                    }

                    val valid = key.reset()
                    if (!valid) {
                        keyMap.remove(key)
                        if (keyMap.isEmpty()) {
                            stop()
                        }
                    }
                }
            } catch (e: InterruptedException) {
                stop()
            } catch (e: IOException) {
                HollowEngine.LOGGER.error(e)
            }
        }.start()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        keyMap.keys.forEach { it.cancel() }
        keyMap.clear()
        watchService.close()
    }

    private fun registerDirectories(dir: Path) {
        try {
            val key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE)
            keyMap[key] = dir

            Files.walk(dir)
                .filter { Files.isDirectory(it) }
                .forEach { subDir ->
                    try {
                        val subKey = subDir.register(watchService, ENTRY_CREATE, ENTRY_DELETE)
                        keyMap[subKey] = subDir
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}