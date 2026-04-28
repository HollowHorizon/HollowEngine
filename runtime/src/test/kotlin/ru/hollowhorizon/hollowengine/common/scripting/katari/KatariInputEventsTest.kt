package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlin.test.Test
import kotlin.test.assertEquals

class KatariInputEventsTest {
    @Test
    fun `key client event maps to server packet snapshot`() {
        val packet = KatariClientInputEvent.Key(
            key = 65,
            scanCode = 30,
            action = KatariInputAction.Press,
            modifiers = 2,
        ).toPacket("player")

        val input = packet.snapshotForTests()
        assertEquals("player", input.playerId)
        assertEquals(KatariInputKind.Key, input.kind)
        assertEquals(KatariInputAction.Press, input.action)
        assertEquals(65, input.key)
        assertEquals(30, input.scanCode)
        assertEquals(2, input.modifiers)
    }

    @Test
    fun `mouse client event preserves coordinates and action`() {
        val packet = KatariClientInputEvent.MouseButton(
            x = 12.5,
            y = 30.0,
            button = 1,
            action = KatariInputAction.Release,
            modifiers = 0,
        ).toPacket("player")

        val input = packet.snapshotForTests()
        assertEquals(KatariInputKind.MouseButton, input.kind)
        assertEquals(KatariInputAction.Release, input.action)
        assertEquals(1, input.button)
        assertEquals(12.5, input.x)
        assertEquals(30.0, input.y)
    }
}
