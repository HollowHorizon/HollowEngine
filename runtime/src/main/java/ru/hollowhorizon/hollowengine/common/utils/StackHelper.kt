/**
 * Utility for handling various ItemStack operations.
 *
 * This file provides methods for comparing, combining, and modifying item stacks.
 */
@file:JvmName("StackHelper")

package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.world.item.ItemStack

/**
 * Checks if two ItemStacks have the same item.
 *
 * @param with The ItemStack to compare with.
 * @return `true` if both stacks have the same item or are both empty, otherwise `false`.
 */
fun ItemStack.areItemsEqual(with: ItemStack): Boolean {
    if (this.isEmpty && with.isEmpty) return true
    return !this.isEmpty && ItemStack.isSameItem(this, with)
}

/**
 * Checks if two ItemStacks have the same item and data components.
 *
 * @param with The ItemStack to compare with.
 * @return `true` if both stacks are identical, otherwise `false`.
 */
fun ItemStack.areStacksEqual(with: ItemStack): Boolean {
    return this.areItemsEqual(with) &&
            ItemStack.isSameItemSameComponents(this, with)

}

/**
 * Checks if two stacks can be combined without exceeding the max stack size.
 *
 * @param stack2 The second ItemStack to check.
 * @return `true` if stacks can be combined, otherwise `false`.
 */
fun ItemStack.canCombineStacks(stack2: ItemStack): Boolean {
    if (!this.isEmpty && stack2.isEmpty) return true
    return this.areStacksEqual(stack2) && this.count + stack2.count <= this.maxStackSize
}

/**
 * Determines if an item stack can combine with another based on count and ingredient count.
 *
 * @param hand The ItemStack to compare with.
 * @param count The number of items in the current stack.
 * @param ingredientCount The required ingredient count.
 * @return `true` if stacks can be combined, otherwise `false`.
 */
fun ItemStack.canCombine(hand: ItemStack, count: Int, ingredientCount: Int): Boolean =
    this.canCombineStacks(hand) && count >= ingredientCount

/**
 * Returns a copy of the ItemStack with the specified size.
 *
 * @param size The desired stack size.
 * @param container Whether to return the crafting remainder if size is zero.
 * @return A new ItemStack with the specified size or an empty stack if size is zero.
 */
fun ItemStack.withSize(size: Int, container: Boolean): ItemStack {
    var itemStack = this
    if (size <= 0) {
        return craftingRemainder?.takeIf { container } ?: ItemStack.EMPTY
    }

    itemStack = itemStack.copy()
    itemStack.count = size

    return itemStack
}

/**
 * Shrinks the ItemStack by a given amount.
 *
 * @param amount The amount to shrink the stack by.
 * @param container Whether to return the crafting remainder if stack becomes empty.
 * @return A new ItemStack with the reduced size or an empty stack if the size is zero.
 */
fun ItemStack.shrink(amount: Int, container: Boolean): ItemStack {
    if (this.isEmpty) return ItemStack.EMPTY

    return withSize(this.count - amount, container)
}

/**
 * Checks if the ItemStack has a crafting remainder.
 */
val ItemStack.hasCraftingRemainder get() = ItemStackUtil.remainer(this) != ItemStack.EMPTY

/**
 * Gets the crafting remainder of the ItemStack.
 */
val ItemStack.craftingRemainder
    get() = ItemStackUtil.remainer(this)
