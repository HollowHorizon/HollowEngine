package ru.hollowhorizon.hollowengine.common.scripting.story

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.coroutines.onMainThreadSync
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.level.LevelEvent
import ru.hollowhorizon.hc.common.events.server.ServerEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.client.gui.DashBoardScreen.draw
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.gui.GuiScript
import ru.hollowhorizon.hollowengine.compiler.suspendable.ResumeState
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendState
import java.io.File
import kotlin.script.experimental.api.valueOrThrow

data class StoryScript(val context: SuspendContext, val event: StoryEvent, val file: String)

val ACTIVE_EVENTS: MutableSet<StoryScript> = hashSetOf()

@SubscribeEvent
fun onStoryTick(event: TickEvent.Server) {
    if (!event.server.isRunning) return

    ACTIVE_EVENTS.removeIf { script ->
        script.context.resetLocks()
        try {
            var result = script.event.tick(script.context)
            while (result == ResumeState) result = script.event.tick(script.context)
            if (result == SuspendState) return@removeIf false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        event.server[StoryScriptStorage::class.java].scripts.remove(script.file)
        return@removeIf true
    }
}

@HollowCapabilityV2(MinecraftServer::class)
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

        startEvent(script, data.tag)
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

    ACTIVE_EVENTS.forEach { script ->
        val file = script.file
        val tag = script.context.serialize()
        scripts[file] = StoryScriptStorage.TagWrapper(tag)
    }
}

fun startEvent(script: File, tag: CompoundTag? = null) {
    scopeAsync {
        val jar = ScriptingCompiler.compileFile<StoryEvent>(script)

        val result = jar.execute()
        val event = result.valueOrThrow().returnValue.scriptInstance as? StoryEvent
            ?: error("Script instance is null")

        onMainThreadSync {
            ACTIVE_EVENTS.add(StoryScript(SuspendContext().apply {
                if (tag != null) deserialize(tag)
            }, event, script.toReadablePath()))
        }
    }
}

fun openGui(script: File, tag: CompoundTag? = null) {
    scopeAsync {
        val jar = ScriptingCompiler.compileFile<GuiScript>(script)

        val result = jar.execute()
        val event = result.valueOrThrow().returnValue.scriptInstance as? GuiScript
            ?: error("Script instance is null")

        onMainThreadSync {
            RenderSystem.recordRenderCall {
                Minecraft.getInstance().setScreen(object: ImGuiScreen() {
                    override fun Graphics.draw() {
                        event.apply {
                            draw()
                        }
                    }
                })
            }
        }
    }
}