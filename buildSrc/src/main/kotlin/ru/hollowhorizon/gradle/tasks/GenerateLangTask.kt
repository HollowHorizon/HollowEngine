package ru.hollowhorizon.gradle.tasks

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateLangTask : DefaultTask() {

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:InputDirectory
    abstract val langDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        allowComments = true // Полезно, если в json затесались комментарии
    }

    @TaskAction
    fun generate() {
        val pkg = generatedPackage.get()
        val inputDir = langDirectory.get().asFile
        val outDir = outputDirectory.get().asFile

        // Берем за основу en_us.json
        val langFile = File(inputDir, "en_us.json")
        if (!langFile.exists()) {
            logger.error("Source file en_us.json not found in ${inputDir.absolutePath}")
            return
        }

        val className = "Lang"
        val outputFile = File(outDir, "${pkg.replace('.', '/')}/$className.kt")

        outputFile.parentFile.mkdirs()

        val translations = json.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            langFile.readText()
        )

        val root = TranslationNode("")

        translations.forEach { (key, value) ->
            val parts = key.split(".")
            var current = root
            parts.forEachIndexed { index, part ->
                val fullPath = parts.take(index + 1).joinToString(".")
                current = current.children.getOrPut(part) { TranslationNode(fullPath) }
                if (index == parts.lastIndex) {
                    current.translationValue = value
                }
            }
        }

        val sb = StringBuilder()
        sb.appendLine("// --- GENERATED CODE, DO NOT MODIFY! --- //")
        sb.appendLine("package $pkg")
        sb.appendLine()
        sb.appendLine("object $className {")

        generateKotlinNodes(sb, root, "    ")

        sb.appendLine("}")

        outputFile.writeText(sb.toString())
    }

    private fun generateKotlinNodes(sb: StringBuilder, node: TranslationNode, indent: String) {
        node.children.entries.sortedBy { it.key }.forEach { (name, child) ->
            val validName = name.toValidIdentifier()

            if (child.children.isEmpty()) {
                if (child.translationValue != null) {
                    sb.appendLine("$indent/** ${child.translationValue} */")
                }
                sb.appendLine("${indent}const val $validName = \"${child.fullKey}\"")
            } else {
                sb.appendLine("${indent}object ${validName.replaceFirstChar { it.uppercase() }} {")

                if (child.translationValue != null) {
                    sb.appendLine("$indent    /** ${child.translationValue} */")
                    sb.appendLine("$indent    const val KEY = \"${child.fullKey}\"")
                }

                generateKotlinNodes(sb, child, "$indent    ")
                sb.appendLine("${indent}}")
            }
        }
    }

    private class TranslationNode(val fullKey: String) {
        val children = mutableMapOf<String, TranslationNode>()
        var translationValue: String? = null
    }

    private fun String.toValidIdentifier(): String {
        // Заменяем недопустимые символы на подчеркивание
        val cleaned = this.replace(Regex("[^a-zA-Z0-9]"), "_")

        // Превращаем в camelCase
        val result = cleaned.split("_")
            .filter { it.isNotEmpty() }
            .mapIndexed { index, part ->
                if (index == 0) part.lowercase() else part.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")

        return when {
            result.isEmpty() -> "key"
            result[0].isDigit() -> "k_$result"
            // Список зарезервированных слов Kotlin (базовый)
            result in setOf("val", "var", "object", "class", "fun", "in", "is") -> "`${result}`"
            else -> result
        }
    }
}
