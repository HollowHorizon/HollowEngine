package ru.hollowhorizon.hollowengine.common.compiler.tools

import java.io.File

internal class ScriptToolArguments(args: Array<String>) {
    private val values = HashMap<String, String>()

    init {
        var index = 0
        while (index < args.size) {
            val argument = args[index]
            require(argument.startsWith("--")) { "Unexpected argument '$argument'" }
            val name = argument.removePrefix("--")
            val value = args.getOrNull(index + 1) ?: error("Missing value for --$name")
            values[name] = value
            index += 2
        }
    }

    fun required(name: String): String = values[name] ?: error("Missing --$name")

    fun optional(name: String): String? = values[name]

    fun list(name: String): List<String> = values[name].orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
}

internal fun currentClasspath(): List<File> = System.getProperty("java.class.path")
    .split(File.pathSeparator)
    .asSequence()
    .filter(String::isNotBlank)
    .map(::File)
    .filter(File::exists)
    .distinctBy { it.absoluteFile.normalize() }
    .toList()

internal fun pruneScriptArtifacts(outputDirectory: File, expected: Collection<File>) {
    if (!outputDirectory.isDirectory) return
    val expectedPaths = expected.mapTo(HashSet()) { it.canonicalPath }
    outputDirectory.walkBottomUp().forEach { file ->
        when {
            file.isFile && file.canonicalPath !in expectedPaths ->
                check(file.delete()) { "Cannot remove stale script artifact '$file'" }

            file.isDirectory && file != outputDirectory && file.list()?.isEmpty() == true ->
                check(file.delete()) { "Cannot remove empty script artifact directory '$file'" }
        }
    }
}
