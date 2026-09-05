package ru.hollowhorizon.hollowengine.common.addons

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.RegistryAccess
import net.minecraft.world.flag.FeatureFlags
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HollowAddonMinecraftApiTests {
    private val coroutineScope = CoroutineScope(Job())
    private val api = OwnedHollowAddonMinecraftApi(ADDON_ID, coroutineScope, javaClass.classLoader)

    @AfterTest
    fun cleanup() {
        coroutineScope.cancel()
        TestEvent.clear()
        RegisterCommandsEvent.clear()
        RegisterCommandsEvent.clearReplaySnapshot()
    }

    @Test
    fun `explicit event subscription follows addon scope`() {
        var calls = 0
        api.subscribe(TestEvent::class) { calls++ }

        TestEvent.post(TestEvent())
        coroutineScope.cancel()
        TestEvent.post(TestEvent())

        assertEquals(1, calls)
    }

    @Test
    fun `closing event registration removes its listener`() {
        var calls = 0
        val registration = api.subscribe(TestEvent::class) { calls++ }

        TestEvent.post(TestEvent())
        registration.close()
        TestEvent.post(TestEvent())

        assertEquals(1, calls)
        assertFalse(registration.isActive)
    }

    @Test
    fun `closing command registration removes nodes added by late replay`() {
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        RegisterCommandsEvent.post(commandEvent(dispatcher))

        val registration = api.registerCommands { commands ->
            commands.register(literal<CommandSourceStack>(COMMAND_NAME).executes { 1 })
        }
        assertNotNull(dispatcher.root.getChild(COMMAND_NAME))

        registration.close()
        assertNull(dispatcher.root.getChild(COMMAND_NAME))
    }

    @Test
    fun `clearing replay snapshot preserves listener for the next dispatcher`() {
        val previous = CommandDispatcher<CommandSourceStack>()
        RegisterCommandsEvent.post(commandEvent(previous))
        RegisterCommandsEvent.clearReplaySnapshot()

        var calls = 0
        api.registerCommands { commands ->
            calls++
            commands.register(literal<CommandSourceStack>(COMMAND_NAME).executes { 1 })
        }
        assertEquals(0, calls)
        assertNull(previous.root.getChild(COMMAND_NAME))

        val current = CommandDispatcher<CommandSourceStack>()
        RegisterCommandsEvent.post(commandEvent(current))
        assertEquals(1, calls)
        assertNotNull(current.root.getChild(COMMAND_NAME))
    }

    private fun commandEvent(dispatcher: CommandDispatcher<CommandSourceStack>) = RegisterCommandsEvent(
        dispatcher = dispatcher,
        registryAccess = CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS),
        environment = Commands.CommandSelection.ALL,
    )

    class TestEvent : Event {
        companion object : EventHandler<TestEvent>()
    }

    private companion object {
        const val ADDON_ID = "minecraft-api-test"
        const val COMMAND_NAME = "addon-test-command"
    }
}
