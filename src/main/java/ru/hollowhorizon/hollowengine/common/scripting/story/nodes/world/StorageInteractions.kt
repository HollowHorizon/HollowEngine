package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.GiveItemList

class StorageItemListNode(itemList: StorageItemList.() -> Unit) : Node() {
    val itemList by lazy { StorageItemList().apply(itemList) }
    var isStarted = false

    override fun tick(): Boolean {
        val block = itemList.level.getBlockEntity(itemList.bpos) ?: return true
        block.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent { itemHandler ->
            itemList.items.removeIf { stack ->
                for (i in 0 until itemHandler.slots) {
                    val item = itemHandler.getStackInSlot(i)
                    if (stack.item == item.item) {
                        val remaining = item.count
                        item.shrink(stack.count)
                        stack.shrink(remaining)
                        return@removeIf stack.isEmpty
                    }
                }
                false
            }
        }
        return itemList.items.isNotEmpty()
    }

    override fun serializeNBT() = CompoundTag().apply {
        put("items", ListTag().apply {
            addAll(itemList.items.map { it.save(CompoundTag()) })
        })
        putString("world", itemList.world)
        putDouble("x", itemList.pos.x)
        putDouble("y", itemList.pos.y)
        putDouble("z", itemList.pos.z)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        itemList.items.clear()
        nbt.getList("items", 10).forEach {
            itemList.items.add(ItemStack.of(it as CompoundTag))
        }
        itemList.world = nbt.getString("world")
        itemList.pos = Vec3(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"))
    }
}

class StorageItemList : GiveItemList() {
    var world = "minecraft:overworld"
    var pos = Vec3.ZERO

    val bpos by lazy { BlockPos(pos) }
    val level by lazy {
        ServerLifecycleHooks.getCurrentServer().getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, world.rl))
            ?: throw IllegalStateException("World $world not found")
    }
}

fun IContextBuilder.waitStorage(items: StorageItemList.() -> Unit) = +StorageItemListNode(items)