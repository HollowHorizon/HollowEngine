package ru.hollowhorizon.hollowengine.common.recipes

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.rl

class CraftingTable {
    companion object {
        fun shaped(item: ItemStack, craft: CraftingTable.() -> Unit) {

        }
    }

    val extra = hashMapOf<String, Any>()

    fun grid(vararg columns: String) {}

    fun where(context: ItemContext.() -> Unit) {

    }

    inner class ItemContext() {
        operator fun Char.minus(item: ItemStack) {

        }
    }
}

fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
    ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalStateException("Item $item not found!"),
    count,
    nbt
)

fun main() {
    CraftingTable.shaped(item("minecraft:diamond", 4)) {
        grid(
            "xxx",
            " y ",
            " y "
        )

        where {
            'x' - item("minecraft:cobblestone")
            'y' - item("minecraft:stick")
        }

        extra["stage"] = "expert"
    }
}
