package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.network.HollowAddonPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.network.HollowAddonPacketRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HollowAddonMinecraftApiTests {
    private val coroutineScope = CoroutineScope(Job())
    private val extensions = OwnedHollowAddonExtensions(AddonId, javaClass.classLoader)
    private val api = OwnedHollowAddonMinecraftApi(AddonId, coroutineScope, extensions, javaClass.classLoader)

    @AfterTest
    fun cleanup() {
        extensions.cleanup()
        coroutineScope.cancel()
        HollowAddonPacketRegistry.unregister(AddonId)
        TestEvent.clear()
    }

    @Test
    fun `explicit event subscription is removed by addon cleanup`() {
        var calls = 0
        api.subscribe(TestEvent::class) { calls++ }

        TestEvent.post(TestEvent())
        extensions.cleanup()
        TestEvent.post(TestEvent())

        assertEquals(1, calls)
    }

    @Test
    fun `explicit packet registration does not require annotation and is reversible`() {
        api.registerPacket(ExplicitPacket::class, HollowPacketHandler.Direction.TO_CLIENT)

        val packet = ExplicitPacket("payload")
        HollowAddonPacketRegistry.encodeForClient(packet)
        extensions.cleanup()

        assertFailsWith<IllegalArgumentException> {
            HollowAddonPacketRegistry.encodeForClient(packet)
        }
    }

    class TestEvent : Event {
        companion object : EventHandler<TestEvent>()
    }

    @Serializable
    private data class ExplicitPacket(val value: String) : HollowAddonPacket {
        override fun handle(player: Player) = Unit
    }

    private companion object {
        const val AddonId = "minecraft-api-test"
    }
}
