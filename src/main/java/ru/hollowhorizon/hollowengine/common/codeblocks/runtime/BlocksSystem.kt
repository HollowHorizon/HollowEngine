package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.usecase.PersistRecoveredScriptUseCase
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.dev.DevLogs
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath

class BlocksSystem(val owner: MinecraftServer) {
    val scripts = mutableMapOf<String, ScriptFile>()
    private val persistRecoveredScript = PersistRecoveredScriptUseCase()

    val format = CodeBlockFormat(BlockRepository.create("Scripts") {
        include(StandardModules.AllBasics)
        include(NPCModule)
        include(EntityModule)
        include(PlayerModule)
        include(WorldModule)
    })

    fun serialize(tag: CompoundTag) {
        val scriptsTag = CompoundTag()
        scripts.forEach { (path, script) ->
            scriptsTag.put(path, CompoundTag().apply(script::serialize))
        }
        tag.put("scripts", scriptsTag)
    }

    fun deserialize(tag: CompoundTag) {
        reloadScripts()

        val scriptsTag = tag.getCompound("scripts")
        scriptsTag.allKeys.forEach { path ->
            scripts[path]?.deserialize(scriptsTag.getCompound(path))
        }
    }

    fun onAttach() {
        BlocksSystemReloadedEvent().post()

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
                    val report = format.loadBlocksWithRecovery(file)
                    val backup = persistRecoveredScript.execute(file, format, report)
                    if (backup != null) {
                        HollowCore.LOGGER.warn(
                            "Script {} recovered with {} issue(s). Backup: {}",
                            readablePath,
                            report.issues.size,
                            backup.toReadablePath()
                        )
                    }
                    val blocks = report.blocks
                    scripts[readablePath] = ScriptFile(this, readablePath, blocks)
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Failed to load script: $readablePath", e)
                }
            }
    }
}

class BlocksSystemReloadedEvent: Event

fun BlocksSystem.getDevHistory(scriptPath: String) = DevLogs.getHistory(scriptPath)
fun BlocksSystem.getDevSlow(scriptPath: String) = DevLogs.getSlow(scriptPath)
fun BlocksSystem.clearDevHistory() = DevLogs.clear()
