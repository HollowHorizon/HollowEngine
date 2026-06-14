package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.serialization.json.Json
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarFile

object HollowAddonManager : AutoCloseable {
    private const val METADATA_PATH = "hollowengine.addon.json"
    private val addonIdPattern = Regex("[a-z0-9_.-]+")
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val loadedAddons = LinkedHashMap<String, LoadedAddon>()

    val loaded: List<HollowAddonMetadata>
        get() = loadedAddons.values.map { it.metadata }

    fun initializeAll(addonsDir: File = DirectoryManager.HOLLOW_ENGINE.resolve("addons").toFile()) {
        if (loadedAddons.isNotEmpty()) return
        if (!addonsDir.exists()) addonsDir.mkdirs()

        val candidates = addonsDir.listFiles { file -> file.isFile && file.extension == "jar" }
            ?.sortedBy { it.name }
            .orEmpty()
            .mapNotNull(::readCandidate)

        candidates
            .groupBy { it.metadata.id }
            .forEach { (id, group) ->
                if (group.size > 1) {
                    val versions = group.joinToString { "${it.file.name}:${it.metadata.version}" }
                    HollowEngine.LOGGER.error("Skipping addon '{}': multiple versions found ({})", id, versions)
                    return@forEach
                }
                load(group.single())
            }
    }

    private fun readCandidate(file: File): AddonCandidate? {
        return runCatching {
            JarFile(file).use { jar ->
                val entry = jar.getJarEntry(METADATA_PATH)
                    ?: throw IllegalArgumentException("Missing $METADATA_PATH")
                val metadata = jar.getInputStream(entry).bufferedReader().use { json.decodeFromString<HollowAddonMetadata>(it.readText()) }
                validate(metadata)
                AddonCandidate(file, metadata)
            }
        }.onFailure { error ->
            HollowEngine.LOGGER.error("Skipping addon jar '{}': {}", file.name, error.message)
        }.getOrNull()
    }

    private fun validate(metadata: HollowAddonMetadata) {
        require(metadata.id.matches(addonIdPattern)) {
            "Invalid addon id '${metadata.id}'"
        }
        require(metadata.version.isNotBlank()) {
            "Addon '${metadata.id}' has empty version"
        }
        require(metadata.entrypoint.isNotBlank()) {
            "Addon '${metadata.id}' has empty entrypoint"
        }
    }

    private fun load(candidate: AddonCandidate) {
        val libraryUrls = extractBundledLibraries(candidate)
            .map { it.toURI().toURL() }
        val classLoader = HollowAddonClassLoader(
            (libraryUrls + candidate.file.toURI().toURL()).toTypedArray(),
            HollowAddonEntrypoint::class.java.classLoader,
        )
        runCatching {
            validateRequiredClasses(candidate.metadata, classLoader)
            val entrypoint = Class.forName(candidate.metadata.entrypoint, true, classLoader)
                .asSubclass(HollowAddonEntrypoint::class.java)
                .getDeclaredConstructor()
                .newInstance()
            val context = HollowAddonContext(candidate.metadata, candidate.file, classLoader)
            entrypoint.initialize(context)
            loadedAddons[candidate.metadata.id] = LoadedAddon(candidate.metadata, entrypoint, classLoader)
            HollowEngine.LOGGER.info("Loaded HollowEngine addon {} {}", candidate.metadata.id, candidate.metadata.version)
        }.onFailure { error ->
            HollowEngine.LOGGER.error("Failed to load addon '{}'", candidate.metadata.id, error)
            classLoader.close()
        }
    }

    private fun validateRequiredClasses(metadata: HollowAddonMetadata, classLoader: ClassLoader) {
        metadata.requiredClasses.forEach { className ->
            runCatching {
                Class.forName(className, false, classLoader)
            }.onFailure { error ->
                throw IllegalStateException("Addon '${metadata.id}' is missing required class '$className'", error)
            }
        }
    }

    private fun extractBundledLibraries(candidate: AddonCandidate): List<File> {
        val outputDirectory = DirectoryManager.HOLLOW_ENGINE
            .resolve(".cache")
            .resolve("addons")
            .resolve(candidate.metadata.id)
            .resolve(candidate.metadata.version)
            .toFile()
        if (!outputDirectory.exists()) outputDirectory.mkdirs()

        return JarFile(candidate.file).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("hollowengine-addon-libs/") && it.name.endsWith(".jar") }
                .map { entry ->
                    val outputFile = outputDirectory.resolve(entry.name.substringAfterLast('/'))
                    jar.getInputStream(entry).use { input ->
                        Files.copy(input, outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    outputFile
                }
                .toList()
        }
    }

    override fun close() {
        loadedAddons.values.toList().asReversed().forEach { addon ->
            runCatching { addon.entrypoint.close() }
                .onFailure { HollowEngine.LOGGER.error("Failed to close addon '{}'", addon.metadata.id, it) }
            runCatching { addon.classLoader.close() }
                .onFailure { HollowEngine.LOGGER.error("Failed to close addon classloader '{}'", addon.metadata.id, it) }
        }
        loadedAddons.clear()
    }

    private data class AddonCandidate(val file: File, val metadata: HollowAddonMetadata)

    private data class LoadedAddon(
        val metadata: HollowAddonMetadata,
        val entrypoint: HollowAddonEntrypoint,
        val classLoader: URLClassLoader,
    )
}

private class HollowAddonClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    private val parentFirstPackages = listOf(
        "ru.hollowhorizon.hollowengine.Hollow",
        "ru.hollowhorizon.hollowengine.common.addons.",
        "ru.hollowhorizon.hollowengine.common.files.",
        "ru.hollowhorizon.hollowengine.common.scripting.",
        "ru.hollowhorizon.hollowengine.common.utils.",
        "ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.",
        "kotlin.annotation.",
        "kotlin.collections.",
        "kotlin.comparisons.",
        "kotlin.concurrent.",
        "kotlin.contracts.",
        "kotlin.coroutines.",
        "kotlin.enums.",
        "kotlin.experimental.",
        "kotlin.internal.",
        "kotlin.io.",
        "kotlin.jvm.",
        "kotlin.math.",
        "kotlin.properties.",
        "kotlin.random.",
        "kotlin.ranges.",
        "kotlin.sequences.",
        "kotlin.text.",
        "kotlin.time.",
        "net.minecraft.",
        "com.mojang.",
        "org.slf4j.",
        "org.apache.logging.",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }

            if (isParentFirst(name)) {
                return super.loadClass(name, resolve)
            }

            val loaded = runCatching { findClass(name) }
                .getOrElse { return super.loadClass(name, resolve) }
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }

    private fun isParentFirst(name: String): Boolean {
        if (parentFirstPackages.any(name::startsWith)) return true

        if (name.startsWith("kotlin.reflect.")) {
            val reflectClassName = name.removePrefix("kotlin.reflect.")
            return !reflectClassName.contains('.')
        }

        return false
    }
}
