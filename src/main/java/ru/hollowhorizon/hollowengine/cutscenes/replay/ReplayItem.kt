package ru.hollowhorizon.hollowengine.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundNBT
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.client.utils.toRL

@Serializable
class ReplayItem(private val item: String, private val count: Int, private val nbt: @Serializable(ForCompoundNBT::class) CompoundNBT?) {
    constructor(itemStack: ItemStack) : this(
        itemStack.item.registryName.toString(),
        itemStack.count,
        itemStack.tag
    )

    fun toStack(): ItemStack {
        val item = ForgeRegistries.ITEMS.getValue(item.toRL()) ?: throw IllegalArgumentException("Item $item not found")
        return ItemStack(item, count, nbt)
    }
}