package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.types.ServerComponent
import ru.hollowhorizon.hollowengine.common.utils.rl

class BlocksSystem(server: MinecraftServer) : ServerComponent(server) {
    val scripts = mutableMapOf<String, ScriptFile>()
    val globals = VariableMap()

    val format = CodeBlockFormat(BlockRepository.create("Scripts") {
        include(StandardModules.AllBasics)
        include(NPCModule)
        include(EntityModule)
        include(PlayerModule)
        include(WorldModule)
    })

    override fun serialize(tag: CompoundTag) {
        val scriptsTag = CompoundTag()
        scripts.forEach { (path, script) ->
            scriptsTag.put(path, CompoundTag().apply(script::serialize))
        }
        tag.put("scripts", scriptsTag)
        tag.put("globals", CompoundTag().apply(globals::serialize))
    }

    override fun deserialize(tag: CompoundTag) {
        globals.deserialize(tag.getCompound("globals"))

        reloadScripts()

        val scriptsTag = tag.getCompound("scripts")
        scriptsTag.allKeys.forEach { path ->
            scripts[path]?.deserialize(scriptsTag.getCompound(path))
        }
    }

    fun reloadScripts() {
        scripts.values.forEach { it.stopAll() }
        scripts.clear()

        val scriptDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
        scriptDir.walk()
            .filter { it.isFile && it.extension == "bc" }
            .forEach { file ->
                val readablePath = file.toReadablePath()
                try {
                    val blocks = format.loadBlocks(file)
                    scripts[readablePath] = ScriptFile(this, readablePath, blocks)
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Failed to load script: $readablePath", e)
                }
            }

        scripts.values.forEach { it.startGlobalTriggers() }
    }
}

@SubscribeEvent
fun onServerStart(event: ServerEvent.Starting) {
    (event.server as ComponentDispatcher).container.attach("hollowengine:blocks_system".rl)
}