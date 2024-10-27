package ru.hollowhorizon.hollowengine.common.commands

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hc.common.commands.onRegisterCommands
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.EventListener
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import ru.hollowhorizon.hollowengine.compiler.suspendable.ResumeState
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendState
import kotlin.script.experimental.api.valueOrThrow

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine" {
            "example" {
                runBlocking {
                    val jar = ScriptingCompiler.compileText<StoryEvent>(
                        """
            val npc = npc(pos = pos(95, 69, -70)) // Создаём нового нпс

            npc moveTo server.players.minBy { it.distanceTo(npc) } // Даём задачу дойти до ближайшего игрока
            npc say "Привет!" // Вывод в чат от лица npc
            wait(2.sec) // Приостанавливаем скрипт на 2 секунды
            npc say "Как дела?" // Вывод в чат от лица npc
        """.trimIndent()
                    )

                    val result = jar.execute()
                    val script = result.valueOrThrow().returnValue.scriptInstance as? StoryEvent
                        ?: error("Script instance is null")

                    EventBus.register(Listener(script))
                }
            }
        }
    }
}

class Listener(val story: StoryEvent) : EventListener<TickEvent.Server> {
    private var disable = false
    val context = SuspendContext()

    override fun onEvent(event: TickEvent.Server) {
        if(disable) return
        var result = story.tick(context)
        while (result == ResumeState) result = story.tick(context)
        if (result == SuspendState) return
        disable = true
    }
}