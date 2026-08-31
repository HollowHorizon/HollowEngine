package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object HollowUiResourceAccess {
    private const val VersionCacheNanos = 250_000_000L

    private val textCache = ConcurrentHashMap<ResourceLocation, TextCacheEntry>()
    private val versionCache = ConcurrentHashMap<ResourceLocation, VersionCacheEntry>()
    private val revisions = AtomicLong()

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
        return versionCache.compute(location) { _, previous ->
            if (previous != null && now - previous.checkedAtNanos <= VersionCacheNanos) return@compute previous
            val attributes = try {
                Files.readAttributes(localPath(location), BasicFileAttributes::class.java).takeIf { it.isRegularFile }
            } catch (_: NoSuchFileException) {
                null
            }
            val modified = attributes?.lastModifiedTime()
            val size = attributes?.size()
            val version = if (previous != null && previous.modified == modified && previous.size == size) {
                previous.version
            } else {
                revisions.incrementAndGet()
            }
            VersionCacheEntry(version, now, modified, size)
        }!!.version
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
        val modified: FileTime?,
        val size: Long?,
    )
}
