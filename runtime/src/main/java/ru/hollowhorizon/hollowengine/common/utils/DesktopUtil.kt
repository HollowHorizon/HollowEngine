package ru.hollowhorizon.hollowengine.common.utils

import ru.hollowhorizon.hollowengine.logE
import java.io.File

object DesktopUtil {
    fun openInExplorer(file: File) {
        if (!file.exists()) return

        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("win") -> arrayOf("explorer", "/select,", file.absolutePath)
            os.contains("mac") -> arrayOf("open", "-R", file.absolutePath)
            os.contains("nix") || os.contains("nux") || os.contains("aix") ->
                arrayOf("xdg-open", file.parent)
            else -> throw UnsupportedOperationException("OS does not support file explorer")
        }

        try {
            Runtime.getRuntime().exec(cmd)
        } catch (e: Exception) {
            logE { "Can't open in explorer: ${e.message}" }
        }
    }
}