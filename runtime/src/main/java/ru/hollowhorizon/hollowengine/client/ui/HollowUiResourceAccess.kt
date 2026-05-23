package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import java.io.InputStreamReader
import java.nio.file.Files

object HollowUiResourceAccess {
    fun readText(location: ResourceLocation): String {
        val local = DirectoryManager.HOLLOW_ENGINE.resolve("assets").resolve(location.namespace).resolve(location.path)
        if (Files.isRegularFile(local)) {
            return Files.newBufferedReader(local, Charsets.UTF_8).use { it.readText() }
        }
        return HollowJavaUtils.getResource(location).use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
        }
    }
}
