package ru.hollowhorizon.hollowengine.common.scripting.scene

import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.scopeAsync
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import kotlin.script.experimental.api.valueOrThrow

object SceneScriptManager {
    private val SCRIPTS = HashMap<String, SceneScript>()

    val scripts get() = SCRIPTS.keys

    @SubscribeEvent
    fun serverStart(event: ServerEvent.Starting) {
        val server = event.server
        val scripts = server[SceneScriptStorage::class].scripts

        scripts.keys.forEach { file ->
            scopeAsync {
                val result = ScriptingCompiler.compileFile<SceneScript>(file.fromReadablePath())
                    .execute()

                try {
                    SCRIPTS[file] = (result.valueOrThrow().returnValue.scriptInstance as SceneScript)
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Exception while starting script $file: ", e)
                }
            }
        }
    }

    @SubscribeEvent
    fun tick(event: TickEvent.Server) {
        val server = event.server
        val scripts = server[SceneScriptStorage::class].scripts

        SCRIPTS.filter { !it.value.isStarted }.forEach { (file, script) ->

            if (!script.isLoaded) {
                script.isLoaded = true
                val tag = scripts[file]?.tag ?: return@forEach
                script.stateMachine.tag = tag
                script.load(tag.getCompound("context"))
            }

            if (script.canResume()) {
                script.isStarted = true
                currentServer.coroutineScope.launch {
                    script.stateMachine.start()
                    stopScene(file)
                }
            }
        }
    }

    fun startScene(file: String, state: String) {
        scopeAsync {
            val result = ScriptingCompiler.compileFile<SceneScript>(file.fromReadablePath())
                .execute()

            try {
                val script = result.valueOrThrow().returnValue.scriptInstance as SceneScript
                script.stateMachine.currentState = state
                SCRIPTS[file] = script
            } catch (e: Exception) {
                HollowCore.LOGGER.error("Exception while starting script $file: ", e)
            }
        }
    }

    fun stopScene(file: String) {
        SCRIPTS.remove(file)
        currentServer[SceneScriptStorage::class].scripts.remove(file)
    }

    @SubscribeEvent
    fun onLevelSave(event: LevelEvent.Save) {
        if (!event.level.isClientSide && event.level.dimension() == Level.OVERWORLD) {
            saveScripts(event.level.server ?: return)
        }
    }

    @SubscribeEvent
    fun onServerStop(event: ServerEvent.Stoping) {
        saveScripts(event.server)
        SCRIPTS.clear()
    }

    private fun saveScripts(server: MinecraftServer) {
        val scripts = server[SceneScriptStorage::class].scripts

        SCRIPTS.forEach { (file, scene) ->
            scripts[file] = SceneScriptStorage.TagWrapper(scene.stateMachine.tag)
        }
    }
}

@HollowCapability(MinecraftServer::class)
class SceneScriptStorage : CapabilityInstance() {
    var scripts by syncableMap<String, TagWrapper>()

    @Serializable
    class TagWrapper(val tag: @Serializable(ForCompoundNBT::class) CompoundTag)
}