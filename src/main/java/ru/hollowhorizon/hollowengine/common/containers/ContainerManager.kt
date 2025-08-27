package ru.hollowhorizon.hollowengine.common.containers

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.capabilities.containers.HollowContainer
import ru.hollowhorizon.hollowengine.common.events.container.ContainerEvent
import ru.hollowhorizon.hollowengine.common.events.post
import java.util.*

interface ContainerManager {
    val PLAYERS_HOLD_STACKS: HashMap<UUID, ItemStack>

    fun clickSlot(
        player: Player,
        fromContainer: Container,
        toContainer: Container,
        id: Int,
        leftButton: Boolean,
        doubleClick: Boolean,
        hasShift: Boolean,
    ): Boolean {
        if (fromContainer is HollowContainer) {
            val event = ContainerEvent.OnClick(player, fromContainer, id)
            event.post()
            if (event.isCanceled) return false
        }

        val item = fromContainer.getItem(id)

        val holdStack = PLAYERS_HOLD_STACKS.computeIfAbsent(player.uuid) { ItemStack.EMPTY }

        if (holdStack.isEmpty && !item.isEmpty) {
            //? if >=1.20.1
            /*if (!fromContainer.canTakeItem(toContainer, id, item)) return false*/

            if (fromContainer is HollowContainer) {
                val event = ContainerEvent.OnTake(player, fromContainer, id)
                event.post()
                if (event.isCanceled) return false
            }

            if (leftButton) {
                if (hasShift) {
                    if (fromContainer is Inventory && fromContainer == toContainer) {
                        val original = fromContainer.getItem(id)
                        val range = if (id in (0..<9)) (9..<36) else (0..<9)

                        val slots = range
                            .filter { fromContainer.canPlaceItem(it, original) }
                            .map { it to fromContainer.getItem(it) }
                            .filter {
                                //? if <1.21 {
                                ItemStack.isSameItemSameTags(original, it.second) or
                                        //?} else {

                                        /*ItemStack.isSameItemSameComponents(original, it.second) or
                                        *///?}
                                        it.second.isEmpty
                            }
                            .sortedBy { it.first }

                        var i = 0
                        while (i < slots.size && original.count > 0) {
                            val slotStack = fromContainer.getItem(slots[i].first)

                            if (slotStack.isEmpty) {
                                fromContainer.setItem(slots[i].first, original)
                                fromContainer.setItem(id, ItemStack.EMPTY)
                                return true
                            }

                            val remaining = slotStack.maxStackSize - slotStack.count
                            slotStack.count = (slotStack.count + original.count).coerceAtMost(slotStack.maxStackSize)
                            if (remaining > 0) {
                                original.shrink(remaining)
                                fromContainer.setItem(slots[i].first, slotStack)
                            }

                            i++
                        }
                        fromContainer.setItem(id, original)
                    } else {
                        val original = fromContainer.getItem(id)
                        val range = (0..<toContainer.containerSize)

                        val slots = range
                            .filter { toContainer.canPlaceItem(it, original) }
                            .map { it to toContainer.getItem(it) }
                            .filter {
                                //? if <1.21 {
                                ItemStack.isSameItemSameTags(original, it.second) or
                                        //?} else {

                                        /*ItemStack.isSameItemSameComponents(original, it.second) or
                                        *///?}
                                        it.second.isEmpty
                            }
                            .sortedBy { it.first }

                        var i = 0
                        while (i < slots.size && original.count > 0) {
                            val slotStack = toContainer.getItem(slots[i].first)

                            if (slotStack.isEmpty) {
                                toContainer.setItem(slots[i].first, original)
                                fromContainer.setItem(id, ItemStack.EMPTY)
                                return true
                            }

                            val remaining = slotStack.maxStackSize - slotStack.count
                            slotStack.count = (slotStack.count + original.count).coerceAtMost(slotStack.maxStackSize)
                            if (remaining > 0) {
                                original.shrink(remaining)
                                toContainer.setItem(slots[i].first, slotStack)
                            }

                            i++
                        }
                        fromContainer.setItem(id, original)
                    }
                } else {
                    PLAYERS_HOLD_STACKS[player.uuid] = item.copy()
                    //Взять целиком
                    fromContainer.setItem(id, ItemStack.EMPTY)
                    return true
                }
            } else {
                val newStack = item.copy()
                // взять половину
                if (newStack.count > 1) {
                    val newItem = item.copy().apply { count /= 2 }
                    fromContainer.setItem(id, newItem)
                    newStack.count -= newItem.count
                } else {
                    fromContainer.setItem(id, ItemStack.EMPTY)
                }
                PLAYERS_HOLD_STACKS[player.uuid] = newStack
                return true
            }
        } else {

            if (leftButton || doubleClick) {
                if (item.isEmpty) {
                    if (!holdStack.isEmpty) {
                        //Положить всё
                        if (doubleClick) {
                            val slots = (0..<fromContainer.containerSize)
                                .filter { toContainer.canPlaceItem(it, holdStack) }
                                .map { it to fromContainer.getItem(it) }
                                .filter {
                                    //? if <1.21 {
                                    ItemStack.isSameItemSameTags(holdStack, it.second) or
                                            //?} else {

                                            /*ItemStack.isSameItemSameComponents(holdStack, it.second) or
                                            *///?}
                                            it.second.isEmpty
                                }
                                .sortedBy { it.second.count }

                            val result = holdStack.copy()
                            var i = 0
                            while (i < slots.size && result.count < result.maxStackSize) {
                                val slotStack = fromContainer.getItem(slots[i].first)

                                val remaining = result.maxStackSize - result.count
                                result.count = (result.count + slotStack.count).coerceAtMost(result.maxStackSize)
                                if (remaining > 0) {
                                    slotStack.shrink(remaining)
                                    fromContainer.setItem(slots[i].first, slotStack)
                                }

                                i++
                            }
                            PLAYERS_HOLD_STACKS[player.uuid] = result
                        } else {
                            if (fromContainer is HollowContainer) {
                                val event = ContainerEvent.OnPlace(player, fromContainer, id)
                                event.post()
                                if (event.isCanceled) return false
                            }
                            if (!fromContainer.canPlaceItem(id, holdStack.copy())) return false
                            fromContainer.setItem(id, holdStack.copy())
                            PLAYERS_HOLD_STACKS[player.uuid] = ItemStack.EMPTY
                        }
                        return true
                    }
                } else {
                    if (fromContainer is HollowContainer) {
                        val event = ContainerEvent.OnPlace(player, fromContainer, id)
                        event.post()
                        if (event.isCanceled) return false
                    }

                    if (
                    //? if <1.21 {
                        ItemStack.isSameItemSameTags(holdStack, item)
                    //?} else {

                    /*ItemStack.isSameItemSameComponents(holdStack, item)
                    *///?}
                    ) {
                        if (item.count != item.maxStackSize) {

                            if (fromContainer.canPlaceItem(id, item)) {
                                val count = holdStack.count
                                val remain = item.maxStackSize - item.count
                                val itemToPlace =
                                    item.copy().apply { this.count = (this.count + count).coerceAtMost(maxStackSize) }
                                fromContainer.setItem(id, itemToPlace)
                                holdStack.shrink(remain)
                                return true
                            } else {
                                val count = item.count
                                val remain = holdStack.maxStackSize - holdStack.count
                                PLAYERS_HOLD_STACKS[player.uuid] =
                                    item.copy().apply { this.count = (this.count + count).coerceAtMost(maxStackSize) }
                                fromContainer.setItem(id, item.copy().apply { shrink(remain) })
                                return true
                            }
                        }
                    } else {
                        if (!fromContainer.canPlaceItem(id, holdStack.copy())) return false
                        fromContainer.setItem(id, holdStack.copy())
                        PLAYERS_HOLD_STACKS[player.uuid] = item
                        return true
                    }
                }
            } else {
                if (fromContainer is HollowContainer) {
                    val event = ContainerEvent.OnPlace(player, fromContainer, id)
                    event.post()
                    if (event.isCanceled) return false
                }
                if (!fromContainer.canPlaceItem(id, holdStack)) return false

                if (item.isEmpty) {
                    fromContainer.setItem(id, holdStack.copy().apply { count = 1 })
                    holdStack.shrink(1)
                    return true
                } else if (
                //? if <1.21 {
                    ItemStack.isSameItemSameTags(holdStack, item)
                    //?} else {

                    /*ItemStack.isSameItemSameComponents(holdStack, item)
                    *///?}

                    && item.count != item.maxStackSize
                ) {
                    fromContainer.setItem(id, item.copy().apply { count++ })
                    holdStack.shrink(1)
                    return true
                }
            }
        }


        return false
    }

}

object ClientContainerManager : ContainerManager {
    override val PLAYERS_HOLD_STACKS = HashMap<UUID, ItemStack>()

    override fun clickSlot(
        player: Player,
        fromContainer: Container,
        toContainer: Container,
        id: Int,
        leftButton: Boolean,
        doubleClick: Boolean,
        hasShift: Boolean,
    ): Boolean {
        val capability = (fromContainer as? HollowContainer)?.capability
            ?: (toContainer as? HollowContainer)?.capability
        capability?.createSyncPacket(fromContainer, toContainer, id, leftButton, doubleClick, hasShift)?.send()
        return super.clickSlot(player, fromContainer, toContainer, id, leftButton, doubleClick, hasShift)
    }
}

object ServerContainerManager : ContainerManager {
    override val PLAYERS_HOLD_STACKS = HashMap<UUID, ItemStack>()
}