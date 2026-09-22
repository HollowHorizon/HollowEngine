package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An editing zone (`copyOnClick`) writes a copy and never moves anything.
 */
class CopySlotTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    private val layout = SlotLayout(
        zones = listOf(
            SlotZoneLayout(name = "npc", offset = 0, size = 2, copyOnClick = true),
            SlotZoneLayout(name = "player", offset = 2, size = 2),
        ),
    )

    private fun state(block: SlotState.() -> Unit = {}) = SlotState(layout.totalSize).apply(block)

    @Test
    fun `placing into an editing slot copies and leaves the cursor holding its stack`() {
        val state = state { carried = ItemStack(Items.DIAMOND, 5) }

        val result = layout.applyClick(state, SlotIntent.click(0, SlotButton.LEFT))

        assertTrue(result.changed)
        assertEquals(5, state[0].count, "the slot takes the whole carried stack")
        assertEquals(5, state.carried.count, "and the player keeps theirs")
    }

    @Test
    fun `an empty-handed click clears an editing slot instead of picking it up`() {
        val state = state { this[0] = ItemStack(Items.DIAMOND, 3) }

        val result = layout.applyClick(state, SlotIntent.click(0, SlotButton.LEFT))

        assertTrue(result.changed)
        assertTrue(state[0].isEmpty)
        assertTrue(state.carried.isEmpty, "an editor must not be a way to take items out")
    }

    @Test
    fun `the right button places exactly one, still without spending it`() {
        val state = state { carried = ItemStack(Items.DIAMOND, 5) }

        layout.applyClick(state, SlotIntent.click(1, SlotButton.RIGHT))

        assertEquals(1, state[1].count)
        assertEquals(5, state.carried.count)
    }

    @Test
    fun `dropping from an editing slot clears it and drops nothing`() {
        val state = state { this[0] = ItemStack(Items.DIAMOND, 3) }

        val result = layout.applyClick(state, SlotIntent.drop(0, all = true))

        assertTrue(result.changed)
        assertTrue(state[0].isEmpty)
        assertTrue(result.dropped.isEmpty(), "the contents were a copy, so there is nothing to drop")
    }

    @Test
    fun `an ordinary zone still moves items`() {
        val state = state { carried = ItemStack(Items.DIAMOND, 5) }

        layout.applyClick(state, SlotIntent.click(2, SlotButton.LEFT))

        assertEquals(5, state[2].count)
        assertTrue(state.carried.isEmpty, "a normal slot consumes what it takes")
    }
}
