package ru.hollowhorizon.hollowengine.common.config

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.LOGGER
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.utils.toml.TomlFormat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private val CONFIGS = HashSet<Config<*>>()

class Config<T : Saveable>(
    val creator: () -> T,
    val name: String,
    val decoder: (InputStream) -> T,
    val encoder: (T) -> String,
) : ReadOnlyProperty<Any?, T> {
    val file = File("").resolve("config/$name.toml")
    lateinit var value: T

    init {
        CONFIGS.add(this)
        reload()
    }

    fun reload() {
        var save = false
        if (file.exists()) {
            LOGGER.info("Loading config: $name.toml")
            try {
                FileInputStream(file).use {
                    value = decoder(it)
                }
            } catch (e: Exception) {
                LOGGER.warn("Error when loading config '{}':", "$name.toml", e)
                value = creator()
                save = true
            }
        } else {
            value = creator()
            save = true
        }
        value.save = {
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.createNewFile()
            }
            file.writeText(encoder(value))
        }
        if (save) value.save()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }
}

@SubscribeEvent
fun registerReloadListeners(event: RegisterReloadListenersEvent.Server) {
    event.register(ResourceManagerReloadListener {
        CONFIGS.forEach { it.reload() }
    })
}

inline fun <reified T : Saveable> hollowConfig(noinline value: () -> T, name: String): ReadOnlyProperty<Any?, T> {
    val decoder: (InputStream) -> T = { inputStream: InputStream ->
        val text = inputStream.bufferedReader().readText()
        TomlFormat.decodeFromString<T>(text)
    }
    val encoder: (T) -> String = { variable: T ->
        TomlFormat.encodeToString(variable)
    }
    return Config(value, name, decoder, encoder)
}

abstract class HollowConfig : Saveable {
    override lateinit var save: () -> Unit
}

interface Saveable {
    var save: () -> Unit
}