package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import ru.hollowhorizon.hollowengine.common.npcs.items.ItemMatchMode
import ru.hollowhorizon.hollowengine.common.npcs.items.itemFilter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the shared stacking core, exercised through [SlotSource] directly.
 *
 * The point of these is the behavior NPC inventories cannot show on their own: a per-slot cap and a
 * storage that refuses an item. Both a slot UI and a script's `giveItem` go through this same code, so a
 * one-item slot has to be honoured no matter which called it.
 */
class SlotOperationsTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    /** A source that caps every slot at one item, the way an equipment slot does. */
    private class SingleItemSource(size: Int) : SlotSource {
        private val backing = SimpleSlotSource(size)
        override val size: Int get() = backing.size
        override fun stackLimit(slot: Int): Int = 1
        override fun get(slot: Int) = backing[slot]
        override fun set(slot: Int, stack: ItemStack) {
            backing[slot] = stack
        }
    }

    /** A source that only accepts apples, the way a vanilla container's `canPlaceItem` might. */
    private class ApplesOnlySource(size: Int) : SlotSource {
        private val backing = SimpleSlotSource(size)
        override val size: Int get() = backing.size
        override fun get(slot: Int) = backing[slot]
        override fun set(slot: Int, stack: ItemStack) {
            backing[slot] = stack
        }

        override fun canPlace(slot: Int, stack: ItemStack): Boolean = stack.`is`(Items.APPLE)
    }

    @Test
    fun `insert fills partial stacks before empty slots`() {
        val source = SimpleSlotSource(3)
        source[1] = ItemStack(Items.APPLE, 60)

        val remainder = source.insert(ItemStack(Items.APPLE, 10))

        assertEquals(64, source[1].count)
        assertEquals(6, source[0].count)
        assertTrue(remainder.isEmpty)
    }

    @Test
    fun `insert respects a per-slot limit and reports the overflow`() {
        val source = SingleItemSource(2)

        val remainder = source.insert(ItemStack(Items.APPLE, 5))

        assertEquals(1, source[0].count)
        assertEquals(1, source[1].count)
        assertEquals(3, remainder.count)
    }

    @Test
    fun `insert leaves items alone when the storage refuses them`() {
        val source = ApplesOnlySource(2)

        assertTrue(source.insert(ItemStack(Items.APPLE, 2)).isEmpty)
        assertEquals(2, source.insert(ItemStack(Items.CARROT, 2)).count)
        assertTrue(source[1].isEmpty)
    }

    @Test
    fun `extract takes only matching items and stops at the requested count`() {
        val source = SimpleSlotSource(3)
        source[0] = ItemStack(Items.APPLE, 4)
        source[1] = ItemStack(Items.CARROT, 4)
        source[2] = ItemStack(Items.APPLE, 4)

        val apples = itemFilter(ItemStack(Items.APPLE), match = ItemMatchMode.ITEM_ONLY)
        val taken = source.extract(apples, 6)

        assertEquals(6, taken.sumOf { it.count })
        assertEquals(2, source.count(apples))
        assertEquals(4, source[1].count)
    }

    @Test
    fun `a range covers only its own slots and writes through`() {
        val backing = SimpleSlotSource(4)
        backing[0] = ItemStack(Items.APPLE, 1)
        val tail = backing.range(2, 2)

        assertEquals(2, tail.size)
        assertTrue(tail[0].isEmpty)

        tail.insert(ItemStack(Items.CARROT, 3))

        assertEquals(3, backing[2].count)
        assertEquals(1, backing[0].count)
    }

    @Test
    fun `clearAll empties the source and hands back what it held`() {
        val source = SimpleSlotSource(2)
        source[0] = ItemStack(Items.APPLE, 2)

        val removed = source.clearAll()

        assertEquals(1, removed.size)
        assertEquals(2, removed.single().count)
        assertTrue(source[0].isEmpty)
    }
}
