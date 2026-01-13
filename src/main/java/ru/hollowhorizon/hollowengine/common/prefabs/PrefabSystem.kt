package ru.hollowhorizon.hollowengine.common.prefabs

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.api.ReloadListener
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.File

@ReloadListener
object PrefabSystem : ResourceManagerReloadListener {
    val prefabs: File = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        prefabs.walk().filter { it.isFile }.forEach(::loadPrefab)
    }

    private fun loadPrefab(file: File) {

    }
}