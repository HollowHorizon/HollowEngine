package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScopedCommandRegistrationTests {
    @Test
    fun `subcommand can be registered removed and registered again`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(LiteralArgumentBuilder.literal(COMMAND_ROOT))

        registerCommand(dispatcher).cancel()
        assertNull(dispatcher.root.getChild(COMMAND_ROOT).getChild(COMMAND_NAME))

        val reloadedScope = registerCommand(dispatcher)
        assertNotNull(dispatcher.root.getChild(COMMAND_ROOT).getChild(COMMAND_NAME))

        reloadedScope.cancel()
        assertNull(dispatcher.root.getChild(COMMAND_ROOT).getChild(COMMAND_NAME))
        assertNotNull(dispatcher.root.getChild(COMMAND_ROOT))
    }

    private fun registerCommand(dispatcher: CommandDispatcher<String>): CoroutineScope {
        val scope = CoroutineScope(Job())
        val registration = ScopedCommandRegistration<String>(scope) { mutation -> mutation() }
        registration.register(dispatcher) {
            dispatcher.register(
                LiteralArgumentBuilder.literal<String>(COMMAND_ROOT)
                    .then(
                        LiteralArgumentBuilder.literal<String>(COMMAND_NAME)
                            .executes { 1 },
                    ),
            )
        }
        assertNotNull(dispatcher.root.getChild(COMMAND_ROOT).getChild(COMMAND_NAME))
        return scope
    }

    private companion object {
        const val COMMAND_ROOT = "host-command"
        const val COMMAND_NAME = "hot-command"
    }
}
