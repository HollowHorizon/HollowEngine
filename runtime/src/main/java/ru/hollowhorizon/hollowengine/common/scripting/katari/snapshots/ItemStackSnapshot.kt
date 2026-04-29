package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStack

@Serializable
@SerialName("hollowengine:katari/itemstack")
@ScriptType("ItemStack")
class ItemStackSnapshot(val item: @Serializable(ForItemStack::class) ItemStack) : ValueSnapshot(), ScriptSnapshot<ItemStack> {
    override suspend fun restore(context: ValueRestoreContext): ItemStack {
        return item
    }

    companion object : ScriptSnapshotFactory<ItemStack, ItemStackSnapshot> {
        override fun capture(value: ItemStack): ItemStackSnapshot {
            return ItemStackSnapshot(value)
        }
    }
}
