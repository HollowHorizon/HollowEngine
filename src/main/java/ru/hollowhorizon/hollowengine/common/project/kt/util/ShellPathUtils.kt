package ru.hollowhorizon.hollowengine.common.project.kt.util

import ru.hollowhorizon.hollowengine.HollowEngine
import java.nio.file.Paths
import java.nio.file.Path
import java.io.File

internal val userHome = Paths.get(System.getProperty("user.home"))

internal fun isOSWindows() = (File.separatorChar == '\\')

fun findCommandOnPath(name: String): Path? =
    if (isOSWindows()) windowsCommand(name)
    else unixCommand(name)

private fun windowsCommand(name: String) =
    findExecutableOnPath("$name.cmd")
        ?: findExecutableOnPath("$name.bat")
        ?: findExecutableOnPath("$name.exe")

private fun unixCommand(name: String) = findExecutableOnPath(name)

private fun findExecutableOnPath(fileName: String): Path? {
    for (dir in System.getenv("PATH").split(File.pathSeparator)) {
        val file = File(dir, fileName)

        if (file.isFile && file.canExecute()) {
            HollowEngine.LOGGER.info("Found {} at {}", fileName, file.absolutePath)

            return Paths.get(file.absolutePath)
        }
    }

    return null
}

fun findProjectCommandWithName(name: String, projectFile: Path): Path? =
    if (isOSWindows()) {
        findFileRelativeToProjectFile("$name.cmd", projectFile)
    } else {
        findFileRelativeToProjectFile(name, projectFile)
    }

private fun findFileRelativeToProjectFile(name: String, projectFile: Path): Path? =
    projectFile.resolveSibling(name).toFile().takeIf { file ->
        file.isFile && file.canExecute()
    }?.let { file ->
        Paths.get(file.absolutePath)
    }