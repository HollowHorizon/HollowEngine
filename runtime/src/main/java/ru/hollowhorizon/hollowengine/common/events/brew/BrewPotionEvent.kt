package ru.hollowhorizon.hollowengine.common.events.brew

import net.minecraft.core.NonNullList
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

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

    class Pre(stacks: NonNullList<ItemStack>) : BrewPotionEvent(stacks), Cancellable {
        companion object: EventHandler<Pre>()
        override var isCanceled: Boolean = false
    }

    class Post(stacks: NonNullList<ItemStack>) : BrewPotionEvent(stacks) {
        companion object: EventHandler<Post>()
    }
}