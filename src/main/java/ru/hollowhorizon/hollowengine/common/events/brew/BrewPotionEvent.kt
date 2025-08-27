package ru.hollowhorizon.hollowengine.common.events.brew

import net.minecraft.core.NonNullList
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event

open class BrewPotionEvent(private val stacks: NonNullList<ItemStack>): Event {
    val size = stacks.size

    fun getItem(index: Int): ItemStack {
        if (index < 0 || index >= stacks.size) return ItemStack.EMPTY
        return stacks[index]
    }

    fun setItem(index: Int, stack: ItemStack) {
        if (index < stacks.size)
            stacks[index] = stack
    }

    class Pre(stacks: NonNullList<ItemStack>) : BrewPotionEvent(stacks), Cancelable {
        override var isCanceled: Boolean = false
    }

    class Post(stacks: NonNullList<ItemStack>) : BrewPotionEvent(stacks)
}