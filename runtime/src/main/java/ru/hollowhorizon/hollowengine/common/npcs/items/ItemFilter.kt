package ru.hollowhorizon.hollowengine.common.npcs.items

import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.utils.areItemsEqual
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual
import ru.hollowhorizon.hollowengine.common.utils.rl

enum class ItemMatchMode {
    ITEM_ONLY,
    ITEM_AND_COMPONENTS,
}

fun interface ItemFilter {
    fun matches(stack: ItemStack): Boolean
}

/** A quantity-free item matcher paired with the amount an operation should satisfy. */
class ItemRequest internal constructor(val filter: ItemFilter, val count: Int) {
    init {
        require(count > 0) { "Requested item count must be greater than zero" }
    }
}

fun itemFilter(
    vararg templates: ItemStack,
    match: ItemMatchMode = ItemMatchMode.ITEM_AND_COMPONENTS,
): ItemFilter {
    // Template counts are deliberately ignored: quantity belongs to ItemRequest.
    val copies = templates.filterNot(ItemStack::isEmpty).map { template ->
        template.copy().apply { count = 1 }
    }
    require(copies.isNotEmpty()) { "At least one non-empty item template is required" }
    return ItemFilter { stack ->
        copies.any { template ->
            when (match) {
                ItemMatchMode.ITEM_ONLY -> template.areItemsEqual(stack)
                ItemMatchMode.ITEM_AND_COMPONENTS -> template.areStacksEqual(stack)
            }
        }
    }
}

fun itemTag(tag: String): ItemFilter = itemTag(TagKey.create(Registries.ITEM, tag.rl))

fun itemTag(tag: TagKey<Item>): ItemFilter = ItemFilter { stack -> stack.`is`(tag) }

fun itemFilter(predicate: (ItemStack) -> Boolean): ItemFilter = ItemFilter(predicate)

fun itemRequest(
    template: ItemStack,
    match: ItemMatchMode = ItemMatchMode.ITEM_AND_COMPONENTS,
): ItemRequest {
    require(!template.isEmpty) { "Item request template cannot be empty" }
    // A stack is the concise request form: its count becomes the requested quantity.
    return ItemRequest(itemFilter(template, match = match), template.count)
}

fun itemRequest(filter: ItemFilter, count: Int): ItemRequest = ItemRequest(filter, count)

fun ItemFilter.request(count: Int): ItemRequest = itemRequest(this, count)

fun itemRequests(
    vararg templates: ItemStack,
    match: ItemMatchMode = ItemMatchMode.ITEM_AND_COMPONENTS,
): List<ItemRequest> = templates.map { template -> itemRequest(template, match) }

fun anyOf(vararg filters: ItemFilter): ItemFilter {
    require(filters.isNotEmpty()) { "At least one item filter is required" }
    return ItemFilter { stack -> filters.any { it.matches(stack) } }
}

fun allOf(vararg filters: ItemFilter): ItemFilter {
    require(filters.isNotEmpty()) { "At least one item filter is required" }
    return ItemFilter { stack -> filters.all { it.matches(stack) } }
}

fun not(filter: ItemFilter): ItemFilter = ItemFilter { stack -> !filter.matches(stack) }
