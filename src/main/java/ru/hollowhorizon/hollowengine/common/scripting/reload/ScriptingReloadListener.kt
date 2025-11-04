package ru.hollowhorizon.hollowengine.common.scripting.reload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import java.io.File

object ScriptingReloadListener : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        runBlocking {
            DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile().walk()
                .filter { it.name.endsWith("-component.kts") }.forEach {
                    withContext(Dispatchers.IO) {
                        compileFile(it)
                    }
                }
        }
    }

    suspend fun compileFile(file: File) {
        //ScriptingCompiler.compileFile(file)
    }
}