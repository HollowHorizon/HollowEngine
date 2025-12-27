package ru.hollowhorizon.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

abstract class GenerateAssetsTask : DefaultTask() {

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:InputDirectory
    abstract val assetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build"
        description = "Generates Kotlin object with references to mod assets scanning all namespaces."
    }

    @TaskAction
    fun generate() {
        val pkg = generatedPackage.get()
        val inputDir = assetsDirectory.get().asFile
        val outDir = outputDirectory.get().asFile

        if (!inputDir.exists()) return

        val className = "Assets"
        val outputFile = File(outDir, "${pkg.replace('.', '/')}/$className.kt")

        if (outputFile.exists()) {
            outputFile.setWritable(true)
        }

        outputFile.parentFile.mkdirs()

        val sb = StringBuilder()

        // Хедер файла
        sb.appendLine("// --- GENERATED CODE, DO NOT MODIFY! --- //")
        sb.appendLine("package $pkg")
        sb.appendLine()
        sb.appendLine("import ru.hollowhorizon.hollowengine.common.utils.rl")
        sb.appendLine()
        sb.appendLine("object $className {")

        inputDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { namespaceDir ->
                val namespace = namespaceDir.name
                val objectName = namespace.toPascalCase()

                sb.appendLine("    object $objectName {")
                walk(namespaceDir, sb, "    ", namespace, "")
                sb.appendLine("    }")
            }

        sb.appendLine("}")
        outputFile.writeText(sb.toString())
        outputFile.setReadOnly()
    }

    private fun walk(dir: File, sb: StringBuilder, indent: String, namespace: String, relativePath: String) {
        val allFiles = dir.listFiles() ?: return

        allFiles.filter { it.isDirectory }
            .sortedBy { it.name }
            .forEach { subDir ->
                val objName = subDir.name.toPascalCase()
                sb.appendLine("$indent    object $objName {")
                walk(subDir, sb, "$indent    ", namespace, "$relativePath${subDir.name}/")
                sb.appendLine("$indent    }")
            }

        val validFiles = allFiles.filter {
            it.isFile && !it.name.endsWith(".mcmeta") && !it.name.startsWith(".")
        }

        val groupedFiles = validFiles.groupBy { it.nameWithoutExtension }

        groupedFiles.toSortedMap().forEach { (baseName, filesInGroup) ->
            if (filesInGroup.size == 1) {
                val file = filesInGroup.first()
                writeField(sb, indent, baseName, namespace, relativePath, file.name)
            } else {
                filesInGroup.sortedBy { it.extension }.forEach { file ->
                    val uniqueName = "${baseName}_${file.extension}"
                    writeField(sb, indent, uniqueName, namespace, relativePath, file.name)
                }
            }
        }
    }

    private fun writeField(
        sb: StringBuilder,
        indent: String,
        rawFieldName: String,
        namespace: String,
        relativePath: String,
        fileName: String
    ) {
        val fieldName = rawFieldName.toUpperSnakeCase()
        val resourcePath = "$namespace:$relativePath$fileName"
        sb.appendLine("$indent    val $fieldName = \"$resourcePath\".rl")
    }

    private fun String.toPascalCase(): String =
        split('_', '-', '.').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun String.toUpperSnakeCase(): String =
        replace(Regex("([a-z])([A-Z]+)"), "$1_$2")
            .replace("-", "_")
            .replace(".", "_")
            .uppercase(Locale.ROOT)
            // Если имя начинается с цифры, добавляем префикс
            .let { if (it.firstOrNull()?.isDigit() == true) "NUM_$it" else it }
}