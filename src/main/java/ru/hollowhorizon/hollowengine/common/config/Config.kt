@file:OptIn(InternalSerializationApi::class, ExperimentalAtomicApi::class)

package ru.hollowhorizon.hollowengine.common.config

import kotlinx.coroutines.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import net.peanuuutz.tomlkt.TomlTable
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.config.properties.Properties
import ru.hollowhorizon.hollowengine.common.utils.ObservableList
import ru.hollowhorizon.hollowengine.common.utils.ObservableMap
import ru.hollowhorizon.hollowengine.common.utils.ObservableSet
import ru.hollowhorizon.hollowengine.common.utils.toml.toml
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.readText

open class Config {
    val properties = Properties { markDirty() }
    private lateinit var configFile: Path
    private val isDirty = AtomicBoolean(false)
    private val saveLock = Any()
    private var lastSaveTime = 0L

    fun initialize() {
        javaClass.getAnnotation(ConfigName::class.java)?.let { annotation ->
            val name = annotation.name
            configFile = CONFIG_DIR.resolve("${name.removeSuffix(".toml")}.toml")

            ensureConfigDirectoryExists()
            loadOrCreateConfig()
            registerFileWatcher()
        } ?: run {
            throw IllegalStateException("Config class ${javaClass.simpleName} must be annotated with @ConfigName")
        }
    }

    private fun loadOrCreateConfig() {
        try {
            if (Files.exists(configFile)) {
                load()
                HollowEngine.LOGGER.info("Config loaded: ${configFile.fileName}")
            } else {
                save(force = true)
                HollowEngine.LOGGER.info("Config created: ${configFile.fileName}")
            }
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Failed to load config ${configFile.fileName}", e)
            createBackupAndRegenerate()
        }
    }

    private fun createBackupAndRegenerate() {
        try {
            if (Files.exists(configFile)) {
                val backupFile =
                    configFile.resolveSibling("${configFile.fileName}.${System.currentTimeMillis()}_backup")
                Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
                HollowEngine.LOGGER.warn("Config backup created: $backupFile")
            }
            save(force = true)
            HollowEngine.LOGGER.info("Config regenerated after error")
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Critical: Failed to regenerate config", e)
        }
    }

    private fun registerFileWatcher() {
        val parentDir = configFile.parent
        try {
            val key = parentDir.register(
                fileWatcher,
                ENTRY_MODIFY
            )
            configRegistrations[key] = FileWatcherEntry(this, configFile.fileName.toString())
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to register file watcher for ${configFile.fileName}", e)
        }
    }

    private fun ensureConfigDirectoryExists() {
        val dir = configFile.parent
        if (!Files.exists(dir)) {
            Files.createDirectories(dir)
        }
    }

    @PublishedApi
    internal fun markDirty() {
        if (isDirty.compareAndSet(expectedValue = false, newValue = true)) {
            scheduleDelayedSave()
        }
    }

    private fun scheduleDelayedSave() {
        saveExecutor.schedule({
            if (isDirty.compareAndSet(expectedValue = true, newValue = false)) {
                save()
            }
        }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    open fun save(force: Boolean = false) {
        synchronized(saveLock) {
            val now = System.currentTimeMillis()
            if (!force && now - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
                return
            }

            try {
                val table = properties.serialize()
                val tempFile = configFile.resolveSibling("${configFile.fileName}.tmp")

                // Atomic write
                Files.write(tempFile, toml.encodeToString(table).toByteArray())
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

                lastSaveTime = now
                HollowEngine.LOGGER.debug("Config saved: {}", configFile.fileName)
            } catch (e: Exception) {
                HollowEngine.LOGGER.error("Failed to save config ${configFile.fileName}", e)
                isDirty.store(true) // Retry later
            }
        }
    }

    open fun load() {
        try {
            val content = configFile.readText()
            val table = toml.decodeFromString<TomlTable>(content)
            properties.deserialize(table)
            HollowEngine.LOGGER.info("Config reloaded: ${configFile.fileName}")
        } catch (e: Exception) {
            HollowEngine.LOGGER.error("Error loading config ${configFile.fileName}", e)
            throw e
        }
    }

    fun <T> property(value: T) = properties.add(value)
    inline fun <reified T : Any> list(vararg value: T) =
        properties.add(ObservableList(mutableListOf(*value), onChange = { markDirty() }, T::class.serializer()))

    inline fun <reified T : Any> set(vararg value: T) =
        properties.add(ObservableSet(mutableSetOf(*value), onChange = { markDirty() }, T::class.serializer()))

    inline fun <reified K : Any, reified V : Any> map(vararg pair: Pair<K, V>) =
        properties.add(
            ObservableMap(
                mutableMapOf(*pair),
                onChange = { markDirty() },
                K::class.serializer(),
                V::class.serializer()
            )
        )

    companion object {
        private const val SAVE_DELAY_MS = 2000L
        private const val MIN_SAVE_INTERVAL_MS = 1000L

        val CONFIG_DIR = Paths.get("config")

        private val fileWatcher = FileSystems.getDefault().newWatchService()
        private val configRegistrations = ConcurrentHashMap<WatchKey, FileWatcherEntry>()
        private val saveExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Config-Save-Thread").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
        private val watchDispatcher = Executors.newFixedThreadPool(1) { runnable ->
            Thread(runnable, "Config-Watcher-Thread").apply {
                isDaemon = true
            }
        }.asCoroutineDispatcher()

        @JvmStatic
        @JvmOverloads
        fun startObserver(scope: CoroutineScope = CoroutineScope(watchDispatcher)) {
            scope.launch {
                while (isActive) {
                    try {
                        val key = fileWatcher.take() ?: continue
                        val entry = configRegistrations[key] ?: continue

                        key.pollEvents().forEach { event ->
                            if (event.kind() == ENTRY_MODIFY && event.context()?.toString() == entry.fileName) {
                                try {
                                    delay(100) // Small delay to ensure file is fully written
                                    entry.config.load()
                                } catch (e: Exception) {
                                    HollowEngine.LOGGER.warn("Failed to reload config on file change", e)
                                }
                            }
                        }

                        if (!key.reset()) {
                            configRegistrations.remove(key)
                        }
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        HollowEngine.LOGGER.error("Error in config file watcher", e)
                    }
                }
            }
        }

        @JvmStatic
        fun shutdown() {
            saveExecutor.shutdown()
            fileWatcher.close()
        }
    }

    private data class FileWatcherEntry(val config: Config, val fileName: String)
}


