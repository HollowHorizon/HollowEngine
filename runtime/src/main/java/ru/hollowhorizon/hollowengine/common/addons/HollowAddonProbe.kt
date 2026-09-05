package ru.hollowhorizon.hollowengine.common.addons

import ru.hollowhorizon.hollowengine.bootstrap.runtime.AddonBootstrapContract
import java.io.File
import java.util.jar.JarFile

internal object HollowAddonProbe {
    fun isAddonJar(file: File): Boolean {
        if (!file.isFile || !file.extension.equals("jar", ignoreCase = true)) return false
        return runCatching {
            JarFile(file, false).use { jar -> jar.getJarEntry(AddonBootstrapContract.DESCRIPTOR_PATH) != null }
        }.getOrDefault(false)
    }

    fun listAddonJars(directory: File): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }.orEmpty()
            .filter(::isAddonJar).sortedBy(File::getName)
}
