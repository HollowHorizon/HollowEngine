package ru.hollowhorizon.hollowengine.common.events.registry

import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import ru.hollowhorizon.hollowengine.common.commands.ScopedCommandRegistration
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.utils.currentServerOrNull

class RegisterCommandsEvent(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val registryAccess: CommandBuildContext,
    val environment: Commands.CommandSelection,
) : Event {
    companion object : EventHandler<RegisterCommandsEvent>() {
        @Volatile
        private var current: RegisterCommandsEvent? = null

        @Synchronized
        override fun register(
            scope: CoroutineScope,
            listener: EventListener<RegisterCommandsEvent>,
        ): EventListener<RegisterCommandsEvent> {
            val registration = ScopedCommandRegistration<CommandSourceStack>(scope, ::executeCommandMutation)
            val trackedListener = object : EventListener<RegisterCommandsEvent> {
                override val priority = listener.priority

                override fun invoke(event: RegisterCommandsEvent) {
                    registration.register(event.dispatcher) { listener(event) }
                }
            }
            val registered = super.register(scope, trackedListener)
            val job = requireNotNull(scope.coroutineContext[Job])
            current?.let { event ->
                executeCommandMutation {
                    if (job.isActive) trackedListener(event)
                }
            }
            return registered
        }

        @Synchronized
        override fun post(event: RegisterCommandsEvent): RegisterCommandsEvent {
            current = event
            return super.post(event)
        }

        /**
         * Drops the dispatcher snapshot used to replay registration to late subscribers.
         * Scoped listeners remain registered and receive the next registration event.
         */
        @Synchronized
        fun clearReplaySnapshot() {
            current = null
        }

        private fun executeCommandMutation(mutation: () -> Unit) {
            val server = currentServerOrNull()
            if (server == null) {
                mutation()
                return
            }

            val mutationWithClientSync = {
                mutation()
                if (!server.isStopped) {
                    server.playerList.players.forEach { player ->
                        server.commands.sendCommands(player)
                    }
                }
            }

            if (server.isStopped || server.isSameThread) {
                mutationWithClientSync()
            } else {
                server.execute(mutationWithClientSync)
            }
        }
    }
}

class RegisterClientCommandsEvent(
    val dispatcher: CommandDispatcher<SharedSuggestionProvider>,
    val registryAccess: CommandBuildContext,
) : Event {
    companion object : EventHandler<RegisterClientCommandsEvent>() {
        @Volatile
        private var current: RegisterClientCommandsEvent? = null

        @Synchronized
        override fun register(
            scope: CoroutineScope,
            listener: EventListener<RegisterClientCommandsEvent>,
        ): EventListener<RegisterClientCommandsEvent> {
            val registration = ScopedCommandRegistration<SharedSuggestionProvider>(scope, ::executeCommandMutation)
            val trackedListener = object : EventListener<RegisterClientCommandsEvent> {
                override val priority = listener.priority

                override fun invoke(event: RegisterClientCommandsEvent) {
                    registration.register(event.dispatcher) { listener(event) }
                }
            }
            val registered = super.register(scope, trackedListener)
            val job = requireNotNull(scope.coroutineContext[Job])
            current?.let { event ->
                executeCommandMutation {
                    if (job.isActive) trackedListener(event)
                }
            }
            return registered
        }

        @Synchronized
        override fun post(event: RegisterClientCommandsEvent): RegisterClientCommandsEvent {
            current = event
            return super.post(event)
        }

        /**
         * Drops the dispatcher snapshot used to replay registration to late subscribers.
         * Scoped listeners remain registered and receive the next registration event.
         */
        @Synchronized
        fun clearReplaySnapshot() {
            current = null
        }

        private fun executeCommandMutation(mutation: () -> Unit) {
            if (current == null) {
                mutation()
                return
            }
            val minecraft = Minecraft.getInstance()
            if (minecraft.isSameThread) mutation() else minecraft.execute(mutation)
        }
    }
}
