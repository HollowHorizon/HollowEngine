package ru.hollowhorizon.hollowengine.common.capabilities.containers

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.Container
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.EntityBlock
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.common.utils.nbt.INBTSerializable
import ru.hollowhorizon.hollowengine.common.utils.readItem
import ru.hollowhorizon.hollowengine.common.utils.save
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityProperty
import ru.hollowhorizon.hollowengine.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent
import ru.hollowhorizon.hollowengine.common.objects.entities.TestEntity

open class HollowContainer(
    val capability: CapabilityInstance,
    val size: Int,
    val canPlace: (slot: Int, stack: ItemStack) -> Boolean,
) : SimpleContainer(size), INBTSerializable {
    override fun setChanged() {
        capability.isChanged = true
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack) = canPlace(slot, stack)

    override fun serialize() =
        ListTag().apply {
            for (i in 0 until this@HollowContainer.size) {
                add(getItem(i).save())
            }
        }

    override fun deserialize(tag: Tag) {
        (tag as ListTag).forEachIndexed { index, tag ->
            setItem(index, (tag as CompoundTag).readItem())
        }
    }

    open class Wrapped(
        private val original: HollowContainer,
        capability: CapabilityInstance,
        private val canExtract: (Int) -> Boolean,
        canPlace: (Int, ItemStack) -> Boolean
    ): HollowContainer(capability, original.size, canPlace) {
        override fun canTakeItem(target: Container, index: Int, stack: ItemStack): Boolean =
            if (this.canExtract(index)) this.original.canTakeItem(target, index, stack) else false

        override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
            if (this.canPlace(slot, stack)) this.original.canPlaceItem(slot, stack) else false
    }
}

fun CapabilityInstance.container(
    size: Int,
    vararg outputSlots: Int,
): CapabilityProperty<CapabilityInstance, HollowContainer> {
    val container = HollowContainer(this, size) { slot, _ -> slot !in outputSlots }
    containers.add(container)
    return syncable(container).apply {
        defaultName = "container${containers.indexOf(container)}"
        defaultType = container.javaClass
    }
}

fun CapabilityInstance.container(container: HollowContainer): CapabilityProperty<CapabilityInstance, HollowContainer> {
    containers.add(container)
    return syncable(container).apply {
        defaultName = "container${containers.indexOf(container)}"
        defaultType = container.javaClass
    }
}

@SubscribeEvent
fun onRemove(event: BlockEvent.Break) {
    if (event.level.isClientSide) return
    val state = event.state
    if (state.block !is EntityBlock) return
    val dispatcher = event.level.getBlockEntity(event.pos) as? ICapabilityDispatcher ?: return
    dispatcher.capabilities.values.forEach {
        it.containers.forEach { container ->
            Containers.dropContents(event.level, event.pos, container)
        }
    }
}

@HollowCapability(TestEntity::class)
class TestEntityCapability : CapabilityInstance() {
    val slots by container(27)
}