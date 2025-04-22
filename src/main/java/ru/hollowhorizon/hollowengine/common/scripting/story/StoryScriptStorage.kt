package ru.hollowhorizon.hollowengine.common.scripting.story

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.util.logE
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.coroutines.onMainThreadSync
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.level.LevelEvent
import ru.hollowhorizon.hc.common.events.server.ServerEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolEvent
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolGuiScripts
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import java.io.File
import kotlin.script.experimental.api.isError
import kotlin.script.experimental.api.valueOrThrow
import kotlin.system.measureTimeMillis

data class StoryScript(val event: StoryEvent, val file: String)

val STORY_EVENTS_SCRIPTS: MutableSet<StoryScript> = hashSetOf()

@SubscribeEvent
fun onStoryTick(event: TickEvent.Server) {
    if (!event.server.isRunning) return

    STORY_EVENTS_SCRIPTS.removeIf { script ->
        try {
            var result = script.event.invoke()
            while (result == ResumeState) result = script.event.invoke()
            if (result == SuspendState) return@removeIf false
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
fun onStoryScriptLoad(event: ServerEvent.Starting) {
    val scripts = event.server[StoryScriptStorage::class.java].scripts

    scripts.forEach { (file, data) ->
        val script = file.fromReadablePath()

        startStoryEvent(script, data.tag)
    }
}

@SubscribeEvent
fun onLevelSave(event: LevelEvent.Save) {
    if (!event.level.isClientSide && event.level.dimension() == Level.OVERWORLD) {
        onStoryScriptSave(currentServer)
    }
}

@SubscribeEvent
fun onServerStop(event: ServerEvent.Stoping) = onStoryScriptSave(event.server)

fun onStoryScriptSave(server: MinecraftServer) {
    val scripts = server[StoryScriptStorage::class.java].scripts

    STORY_EVENTS_SCRIPTS.forEach { script ->
        val file = script.file
//        val tag = script.context.serialize()
//        scripts[file] = StoryScriptStorage.TagWrapper(tag)
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

            onMainThreadSync {
                STORY_EVENTS_SCRIPTS.add(StoryScript(event, script.toReadablePath()))
            }
        }
        HollowCore.LOGGER.info("Story script started in $measureTime ms.")
    }
}

fun startGuiScript(script: File) {
    scopeAsync {
        val jar = ScriptingCompiler.compileFile<KoolEvent>(script)

        val result = jar.execute()
        val event = result.valueOrThrow().returnValue.scriptInstance as? KoolEvent
            ?: error("Script instance is null")

        onMainThreadSync {
            RenderSystem.recordRenderCall {
                val screenMinecraft = Minecraft.getInstance()

                val gui = KoolGuiScripts(event)
                gui.open()
            }
        }
    }
}