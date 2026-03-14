package ru.hollowhorizon.gradle.tasks

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.TreeMap

abstract class MergeLangTask : DefaultTask() {

    @get:Optional
    @get:InputDirectory
    abstract val splitLangDirectory: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    abstract val legacyLangDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        allowComments = true
        prettyPrint = true
    }

    @TaskAction
    fun merge() {
        val outputDir = outputDirectory.get().asFile
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()

        val merged = linkedMapOf<String, LinkedHashMap<String, String>>()
        val origins = linkedMapOf<String, MutableMap<String, String>>()

        mergeLegacyLangFiles(merged, origins)
        mergeSplitLangFiles(merged, origins)

        merged.toSortedMap().forEach { (locale, translations) ->
            val outputFile = File(outputDir, "$locale.json")
            val sortedTranslations = TreeMap(translations)
            outputFile.writeText(
                json.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    sortedTranslations
                ),
                StandardCharsets.UTF_8
            )
        }
    }

    private fun mergeLegacyLangFiles(
        merged: MutableMap<String, LinkedHashMap<String, String>>,
        origins: MutableMap<String, MutableMap<String, String>>
    ) {
        val inputDir = legacyLangDirectory.orNull?.asFile ?: return
        if (!inputDir.exists()) return

        inputDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedBy { it.name }
            ?.forEach { localeFile ->
                val locale = localeFile.nameWithoutExtension
                val translations = decode(localeFile)
                val localeTranslations = merged.getOrPut(locale) { linkedMapOf() }
                val localeOrigins = origins.getOrPut(locale) { linkedMapOf() }

                translations.forEach { (key, value) ->
                    putTranslation(
                        locale = locale,
                        key = key,
                        value = value,
                        source = localeFile.relativeTo(project.projectDir).invariantSeparatorsPath,
                        localeTranslations = localeTranslations,
                        localeOrigins = localeOrigins
                    )
                }
            }
    }

    private fun mergeSplitLangFiles(
        merged: MutableMap<String, LinkedHashMap<String, String>>,
        origins: MutableMap<String, MutableMap<String, String>>
    ) {
        val inputDir = splitLangDirectory.orNull?.asFile ?: return
        if (!inputDir.exists()) return

        inputDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { localeDir ->
                val locale = localeDir.name
                val localeTranslations = merged.getOrPut(locale) { linkedMapOf() }
                val localeOrigins = origins.getOrPut(locale) { linkedMapOf() }

                localeDir.walkTopDown()
                    .filter { it.isFile && it.extension == "json" }
                    .sortedBy { it.relativeTo(localeDir).invariantSeparatorsPath }
                    .forEach { langFile ->
                        val prefix = buildPrefix(localeDir, langFile)
                        decode(langFile).forEach { (key, value) ->
                            val finalKey = when {
                                prefix.isEmpty() -> key
                                key.isEmpty() -> prefix
                                else -> "$prefix.$key"
                            }

                            if (finalKey.isEmpty()) {
                                throw GradleException(
                                    "Translation key in ${langFile.relativeTo(project.projectDir).invariantSeparatorsPath} resolves to an empty key"
                                )
                            }

                            putTranslation(
                                locale = locale,
                                key = finalKey,
                                value = value,
                                source = langFile.relativeTo(project.projectDir).invariantSeparatorsPath,
                                localeTranslations = localeTranslations,
                                localeOrigins = localeOrigins
                            )
                        }
                    }
            }
    }

    private fun putTranslation(
        locale: String,
        key: String,
        value: String,
        source: String,
        localeTranslations: MutableMap<String, String>,
        localeOrigins: MutableMap<String, String>
    ) {
        val previousSource = localeOrigins.putIfAbsent(key, source)
        if (previousSource != null) {
            throw GradleException(
                "Duplicate translation key '$key' for locale '$locale' in '$previousSource' and '$source'"
            )
        }
        localeTranslations[key] = value
    }

    private fun buildPrefix(localeDir: File, langFile: File): String {
        val relativePath = langFile.relativeTo(localeDir).invariantSeparatorsPath
        val withoutExtension = relativePath.removeSuffix(".json")
        if (withoutExtension == "index") return ""
        if (withoutExtension.endsWith("/index")) {
            return withoutExtension.removeSuffix("/index").replace('/', '.')
        }
        return withoutExtension.replace('/', '.')
    }

    private fun decode(file: File): Map<String, String> = json.decodeFromString(
        MapSerializer(String.serializer(), String.serializer()),
        file.readText(StandardCharsets.UTF_8)
    )
}
