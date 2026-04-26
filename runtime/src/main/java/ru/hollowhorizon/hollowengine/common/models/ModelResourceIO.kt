package ru.hollowhorizon.hollowengine.common.models

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files

object ModelResourceIO {
    fun exists(location: ResourceLocation): Boolean =
        folderPath(location)?.let(Files::isRegularFile) == true || classpathExists(location)

    fun open(location: ResourceLocation): InputStream =
        folderPath(location)
            ?.takeIf(Files::isRegularFile)
            ?.let(Files::newInputStream)
            ?: classpathResource(location)
            ?: throw FileNotFoundException("Resource $location not found")

    private fun folderPath(location: ResourceLocation) =
        DirectoryManager.HOLLOW_ENGINE.resolve("assets")
            .resolve(location.namespace)
            .resolve(location.path)
            .normalize()
            .takeIf { path ->
                val assetsRoot = DirectoryManager.HOLLOW_ENGINE.resolve("assets").normalize()
                path.startsWith(assetsRoot)
            }

    private fun classpathResource(location: ResourceLocation): InputStream? {
        val path = "assets/${location.namespace}/${location.path}"
        return Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: ModelResourceIO::class.java.classLoader.getResourceAsStream(path)
    }

    private fun classpathExists(location: ResourceLocation): Boolean {
        val path = "assets/${location.namespace}/${location.path}"
        return Thread.currentThread().contextClassLoader.getResource(path) != null ||
            ModelResourceIO::class.java.classLoader.getResource(path) != null
    }
}
