package ru.hollowhorizon.hollowengine.common.commands

import ru.hollowhorizon.hc.common.commands.onRegisterCommands
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.EventListener
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.getNode
import ru.hollowhorizon.hollowengine.scripting.nodes.Node

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine" {
            "example" {
                class Listener(val node: Node) : EventListener<TickEvent.Server> {
                    override fun onEvent(event: TickEvent.Server) {
                        if (node.execute()) EventBus.unregister(this)
                    }
                }

                val node = getNode()
                EventBus.register(Listener(node))
            }
        }
    }
}