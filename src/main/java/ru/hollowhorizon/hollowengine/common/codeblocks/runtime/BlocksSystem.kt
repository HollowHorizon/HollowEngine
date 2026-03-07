package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.*
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.usecase.PersistRecoveredScriptUseCase
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScope
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScopeRegistry
import ru.hollowhorizon.hollowengine.common.coroutines.ServerScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.dev.DevLogs
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath

class BlocksSystem(val owner: MinecraftServer) {
    val scripts = mutableMapOf<String, ScriptFile>()
    val serverScope = ServerScope(owner) { markDirty() }
    private val persistRecoveredScript = PersistRecoveredScriptUseCase()
    internal var dirtyListener: (() -> Unit)? = null

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
        tag.put("server_scope", CompoundTag().also(serverScope::serialize))
    }

    fun deserialize(tag: CompoundTag) {
        reloadScripts(postEvent = false)

        val scriptsTag = tag.getCompound("scripts")
        scripts.values.forEach { script ->
            val scriptTag = if (scriptsTag.contains(script.path)) scriptsTag.getCompound(script.path) else CompoundTag()
            script.deserialize(scriptTag)
        }
        serverScope.deserialize(tag.getCompound("server_scope"))
        restoreOwnerScopes()
        startEnabledScripts()
        onAttach()
    }

    fun onAttach() {
        BlocksSystemReloadedEvent().post()
    }

    fun markDirty() {
        dirtyListener?.invoke()
    }

    fun reloadScripts(postEvent: Boolean = true) {
        val enabledStates = scripts.mapValues { it.value.isEnabled }

        scripts.values.forEach {
            it.stopRouting()
            it.unregisterRuntimeDefinitions()
        }
        cancelDefinitions("codeblocks:")
        scripts.clear()

        val scriptDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
        scriptDir.walk().filter { it.isFile && it.extension == "bc" }.forEach { file ->
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
                scripts[readablePath] = ScriptFile(this, readablePath, report.blocks).also { script ->
                    script.registerRuntimeDefinitions()
                    script.loadEnabledState(enabledStates[readablePath] ?: true)
                }
            } catch (e: Exception) {
                HollowCore.LOGGER.error("Failed to load script: $readablePath", e)
            }
        }

        restoreOwnerScopes()
        startEnabledScripts()
        if (postEvent) onAttach()
    }

    internal fun ownerScope(ownerKey: OwnerKey): OwnerScope? {
        return when (val key = ownerKey) {
            OwnerKey.Global -> serverScope
            is OwnerKey.Entity -> owner.playerList.players.firstOrNull { it.uuid == key.uuid }?.coroutineScope as? OwnerScope
                ?: owner.allLevels.firstNotNullOfOrNull { level -> level.getEntity(key.uuid)?.coroutineScope as? OwnerScope }
        }
    }

    internal fun cancelDefinitions(prefix: String) {
        ownerScopes().forEach { it.cancelDefinitions(prefix) }
        markDirty()
    }

    internal fun restoreOwnerScopes() {
        ownerScopes().forEach { it.restorePending() }
    }

    private fun startEnabledScripts() {
        scripts.values.filter { it.isEnabled }.forEach { it.startRouting() }
    }

    private fun ownerScopes(): List<OwnerScope> {
        return (OwnerScopeRegistry.scopes() + serverScope).distinct()
    }

    fun emitSignal(signal: ScriptSignal) {
        val targetScripts = when (signal.scope) {
            SignalScope.LOCAL -> listOfNotNull(scripts[signal.sourceScriptPath])
            SignalScope.GLOBAL -> scripts.values.toList()
        }
        targetScripts.forEach { it.launchSignal(signal) }
    }

    suspend fun callSignal(signal: ScriptSignal) {
        val targetScripts = when (signal.scope) {
            SignalScope.LOCAL -> listOfNotNull(scripts[signal.sourceScriptPath])
            SignalScope.GLOBAL -> scripts.values.toList()
        }
        targetScripts.forEach { it.callSignal(signal) }
    }
}

class BlocksSystemReloadedEvent : Event

fun BlocksSystem.getDevHistory(scriptPath: String) = DevLogs.getHistory(scriptPath)
fun BlocksSystem.getDevSlow(scriptPath: String) = DevLogs.getSlow(scriptPath)
fun BlocksSystem.clearDevHistory() = DevLogs.clear()
