package ru.hollowhorizon.hollowengine.common.scripting.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.io.path.exists

private val embedMods = File("hollowcore/embed_mods/").apply {
    if (!exists()) this.mkdirs()
}.toPath()

fun collectModsJars() {
    ModList.mods.forEach { mod ->
        collectModJars(ModList.getFile(mod))
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun collectModJars(file: File) {
    val mod = embedMods.resolve(file.name)
    if (!mod.exists()) Files.copy(file.toPath(), mod)

    val jar = JarFile(file)
    val stream = JsonFormat.decodeFromStream<JarInJars>(
        jar.getInputStream(
            jar.getEntry("META-INF/jarjar/metadata.json") ?: return
        )
    )

    stream.jars.forEach {
        val embedMod = embedMods.resolve(it.path.substringAfterLast("/"))
        val entry = jar.getInputStream(jar.getEntry(it.path))
        if (!embedMod.exists()) Files.copy(entry, embedMod)
        collectModJars(embedMod.toFile())
    }
}

@Serializable
class JarInJars(
    val jars: List<Jar>,
) {
    @Serializable
    class Jar(
        val identifier: Identifier,
        val version: Version,
        val path: String,
        val isObfuscated: Boolean = true,
    ) {
        @Serializable
        data class Identifier(val group: String, val artifact: String)

        @Serializable
        data class Version(val range: String, val artifactVersion: String)
    }
}