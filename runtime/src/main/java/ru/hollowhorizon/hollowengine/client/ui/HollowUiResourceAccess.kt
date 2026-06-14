package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object HollowUiResourceAccess {
    private const val VersionCacheNanos = 250_000_000L

    private val textCache = ConcurrentHashMap<ResourceLocation, TextCacheEntry>()
    private val versionCache = ConcurrentHashMap<ResourceLocation, VersionCacheEntry>()

    fun readText(location: ResourceLocation): String {
        val version = version(location)
        textCache[location]?.takeIf { it.version == version }?.let { return it.text }
        return readTextUncached(location).also { text ->
            textCache[location] = TextCacheEntry(version, text)
        }
    }

    fun version(location: ResourceLocation): Long {
        val now = System.nanoTime()
        versionCache[location]?.takeIf { now - it.checkedAtNanos <= VersionCacheNanos }?.let { return it.version }
        val local = localPath(location)
        val version = if (Files.isRegularFile(local)) Files.getLastModifiedTime(local).toMillis() else 0L
        versionCache[location] = VersionCacheEntry(version, now)
        return version
    }

    fun clearCache() {
        textCache.clear()
        versionCache.clear()
    }

    private fun readTextUncached(location: ResourceLocation): String {
        val local = localPath(location)
        if (Files.isRegularFile(local)) {
            return Files.newBufferedReader(local, Charsets.UTF_8).use { it.readText() }
        }
        return HollowJavaUtils.getResource(location).use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun localPath(location: ResourceLocation) =
        DirectoryManager.HOLLOW_ENGINE.resolve("assets").resolve(location.namespace).resolve(location.path)

    private data class TextCacheEntry(
        val version: Long,
        val text: String,
    )

    private data class VersionCacheEntry(
        val version: Long,
        val checkedAtNanos: Long,
    )
}
