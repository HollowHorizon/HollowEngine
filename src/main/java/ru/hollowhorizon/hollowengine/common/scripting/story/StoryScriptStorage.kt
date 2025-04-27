package ru.hollowhorizon.hollowengine.common.scripting.story

import de.fabmax.kool.util.logE
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.coroutines.onMainThreadSync
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.level.LevelEvent
import ru.hollowhorizon.hc.common.events.server.ServerEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolClientManager
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolScript
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import java.io.File
import kotlin.script.experimental.api.isError
import kotlin.script.experimental.api.valueOrThrow
import kotlin.system.measureTimeMillis

data class StoryScript(val event: StoryEvent, val file: String)

val STORY_EVENTS_SCRIPTS: MutableSet<StoryScript> = hashSetOf()

private var isLoaded = false

@SubscribeEvent
fun onStoryTick(event: TickEvent.Server) {
    if (!event.server.isRunning) return

    if (event.server.playerList.players.isNotEmpty() && event.server.playerList.players.all {
            it.level().isLoaded(it.blockPosition())
        } && !isLoaded) {
        isLoaded = true

        val scripts = event.server[StoryScriptStorage::class.java].scripts

        scripts.forEach { (file, data) ->
            val script = file.fromReadablePath()

            startStoryEvent(script, data.tag)
        }
    }

    STORY_EVENTS_SCRIPTS.removeIf { script ->
        try {
            script.event.updateAsyncs()
            var result = script.event.invoke()
            while (result == ResumeState) result = script.event.invoke()
            if (result == SuspendState) return@removeIf false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val scripts = event.server[StoryScriptStorage::class.java].scripts
        scripts.remove(script.file)
        return@removeIf true
    }
}

@HollowCapability(MinecraftServer::class)
class StoryScriptStorage : CapabilityInstance() {
    var scripts by syncableMap<String, TagWrapper>()

    @Serializable //TODO: Make capabilities support contextual values
    class TagWrapper(val tag: @Serializable(ForCompoundNBT::class) CompoundTag)
}

@SubscribeEvent
fun onLevelSave(event: LevelEvent.Save) {
    if (!event.level.isClientSide && event.level.dimension() == Level.OVERWORLD) {
        onStoryScriptSave(currentServer)
    }
}

@SubscribeEvent
fun onServerStop(event: ServerEvent.Stoping) {
    onStoryScriptSave(event.server)
    STORY_EVENTS_SCRIPTS.clear()
    isLoaded = false
}

fun onStoryScriptSave(server: MinecraftServer) {
    val scripts = server[StoryScriptStorage::class.java].scripts

    STORY_EVENTS_SCRIPTS.forEach { script ->
        try {
            val file = script.file
            val tag = NBTFormat.serialize(script.event.serializer, JavaHacks.forceCast(script.event))
            scripts[file] = StoryScriptStorage.TagWrapper(tag as CompoundTag)
        } catch (e: Exception) {
            HollowCore.LOGGER.error("Error while saving story script ${script.file}", e)
        }
    }
}

fun startStoryEvent(script: File, tag: CompoundTag? = null) {
    scopeAsync {
        val measureTime = measureTimeMillis {
            val jar = ScriptingCompiler.compileFile<StoryEvent>(script)

            val result = jar.execute()
            result.reports.filter { it.isError() }.forEach {
                logE { it.render(withStackTrace = true) }
            }
            val event = result.valueOrThrow().returnValue.scriptInstance as? StoryEvent
                ?: error("Script instance is null")

            tag?.let {
                try {
                    NBTFormat.deserialize(event.serializer, it)
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Error while loading story script ${script.toReadablePath()}", e)
                }
                try {
                    event.restoreState()
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Error while restoring state ${script.toReadablePath()}", e)
                }
            }

            onMainThreadSync {
                STORY_EVENTS_SCRIPTS.add(StoryScript(event, script.toReadablePath()))
            }
        }
        HollowCore.LOGGER.info("Story script started in $measureTime ms.")
    }
}

fun startKoolScript(script: File) {
    scopeAsync {
        val name = script.toReadablePath()
        val jar = ScriptingCompiler.compileFile<KoolScript>(script)

        val result = jar.execute()
        val event = result.valueOrThrow().returnValue.scriptInstance as? KoolScript
            ?: error("Script instance is null")

        if (name in KoolClientManager) {
            KoolClientManager.updateScene(name, CompoundTag())
            return@scopeAsync
        }

        KoolClientManager.addScene(name, event)
    }

}