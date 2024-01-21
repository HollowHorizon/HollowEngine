package ru.hollowhorizon.hollowengine.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.client.utils.rl

@Serializable
class ReplayItem(private val item: String, private val count: Int, private val nbt: @Serializable(ForCompoundNBT::class) CompoundTag?) {
    constructor(itemStack: ItemStack) : this(
        ForgeRegistries.ITEMS.getKey(itemStack.item)?.toString() ?: "",
        itemStack.count,
        itemStack.tag
    )

    fun toStack(): ItemStack {
        val item = ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalArgumentException("Item $item not found")
        return ItemStack(item, count, nbt)
    }
}