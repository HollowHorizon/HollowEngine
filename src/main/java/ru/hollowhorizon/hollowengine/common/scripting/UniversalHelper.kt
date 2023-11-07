package ru.hollowhorizon.hollowengine.common.scripting

import dev.ftb.mods.ftbteams.api.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.rl

fun main() {
    val book = item("minecraft:written_book", 1, "{author:\"Dev\",filtered_title:\"Hollow\",pages:['{\"text\":\"Hello...\"}','{\"text\":\"There is letters\"}','{\"text\":\"на русском тоже!\\n\\n\\nда\"}'],title:\"Hollow\"}")
}

fun Team.get(name: String) = onlineMembers.find { it.gameProfile.name == name }

fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
    ForgeRegistries.ITEMS.getValue(item.rl) ?: throw IllegalStateException("Item $item not found!"),
    count,
    nbt
)

fun item(item: String, count: Int = 1, nbt: String): ItemStack {
    return item(item, count, TagParser.parseTag(nbt))
}

fun tag(tag: String): TagKey<Item> {
    val manager = ForgeRegistries.ITEMS.tags() ?: throw IllegalStateException("Tag $tag not found!")
    return manager.createTagKey(tag.rl)
}