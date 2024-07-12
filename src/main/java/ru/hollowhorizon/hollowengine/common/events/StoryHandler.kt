/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.isLogicalClient
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptStartedPacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptError
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptErrorPacket
import ru.hollowhorizon.hollowengine.common.capabilities.StoriesCapability
import ru.hollowhorizon.hollowengine.common.capabilities.Story
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.StoryLogger
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import ru.hollowhorizon.kotlinscript.common.events.ScriptErrorEvent
import ru.hollowhorizon.kotlinscript.common.events.ScriptStartedEvent
import java.util.concurrent.ConcurrentHashMap

object StoryHandler {
    private val events = ConcurrentHashMap<String, StoryStateMachine>()
    fun getActiveEvents() = events.keys

    fun stopEvent(eventPath: String) {
        events.remove(eventPath)
        ServerLifecycleHooks.getCurrentServer().overworld()[StoriesCapability::class].stories.remove(eventPath)
    }

    fun restartEvent(eventPath: String) {
        events.remove(eventPath)
        runScript(ServerLifecycleHooks.getCurrentServer(), eventPath.fromReadablePath())
    }

    fun getEventByScript(name: StoryStateMachine) = events.entries.find { it.value == name }?.key

    fun getEventByName(name: String) = events[name]

    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as ServerPlayer

        DirectoryManager.joinEvents().forEach { runScript(player.server, it) }

        if (player.stats.getValue(Stats.CUSTOM[Stats.PLAY_TIME]) == 0) {
            DirectoryManager.firstJoinEvents().forEach { runScript(player.server, it) }
        }
    }

    @JvmStatic
    fun onServerTick(event: ServerTickEvent) {
        if (!event.server.isReady || !event.server.overworld().forcedChunks.all {
                event.server.overworld().areEntitiesLoaded(it)
            }) return

        events.entries.removeIf {
            try {
                it.value.tick(event)
                it.value.isEnded
            } catch (exception: Exception) {
                event.server.playerList.players.forEach { player ->
                    player.sendSystemMessage(Component.translatable("hollowengine.executing_error", it.key))
                    player.sendSystemMessage("${exception.message}".mcText)
                    player.sendSystemMessage("hollowengine.check_logs".mcTranslate)
                }

                StoryLogger.LOGGER.error("(HollowEngine) Error while executing event \"${it.key}\"", exception)

                return@removeIf true
            }
        }
    }

    @JvmStatic
    fun onServerShutdown(event: ServerStoppingEvent) {
        val overworld = event.server.overworld()
        overworld[StoriesCapability::class].stories.apply {
            clear()
            putAll(events.map { it.key to Story(it.value.serialize()) }.toMap())
        }
    }

    @JvmStatic
    fun onWorldSave(event: LevelEvent.Save) {
        val server = event.level.server ?: return
        val overworld = server.overworld()
        if (overworld.isClientSide) return

        overworld[StoriesCapability::class].stories.apply {
            clear()
            putAll(events.map { it.key to Story(it.value.serialize()) }.toMap())
        }
    }

    @JvmStatic
    fun onServerStart(event: ServerStartedEvent) {
        if (isLogicalClient) return
        val stories = event.server.overworld()[StoriesCapability::class].stories

        stories.keys.forEach { story ->
            val file = story.fromReadablePath()

            runScript(ServerLifecycleHooks.getCurrentServer(), file)
        }
    }

    @JvmStatic
    fun onScriptError(event: ScriptErrorEvent) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return

        server.playerList.players.forEach { player ->
            ScriptErrorPacket(
                event.file.name,
                event.error.map { ScriptError(it.severity, it.message, it.source, it.line, it.column) }).send(player)
        }
    }

    @JvmStatic
    fun onScriptStarted(event: ScriptStartedEvent) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return

        server.playerList.players.forEach { player ->
            ScriptStartedPacket(event.file.name).send(player)
        }
    }

    fun addStoryEvent(eventPath: String, event: StoryStateMachine, resetProgress: Boolean = false) {
        val savedData = event.server.overworld()[StoriesCapability::class].stories[eventPath]

        if(!resetProgress) {
            savedData?.let {
                event.deserialize(it.nbt)
            }
        }

        event.isStarted = true
        event.startTasks.forEach { it() }

        val old = events.put(eventPath, event)

        old?.cleanup()
    }

}