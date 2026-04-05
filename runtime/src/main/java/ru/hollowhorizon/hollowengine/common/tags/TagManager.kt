package ru.hollowhorizon.hollowengine.common.tags

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Tier
import net.minecraft.world.item.Tiers
import net.minecraft.world.level.block.Block
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterTagsEvent
import ru.hollowhorizon.hollowengine.common.utils.rl

object TagManager {
    val BLOCK_TAGS = HashMap<TagKey<Block>, MutableSet<Block>>()
    val ITEM_TAGS = HashMap<TagKey<Item>, MutableSet<Item>>()

    @SubscribeEvent
    fun registerTags(e: RegisterTagsEvent) {
        if(e.registry.key() == Registries.BLOCK) {
            BLOCK_TAGS.forEach { (tags, blocks) ->
                e.addToTag(tags, *blocks.toTypedArray())
            }
        }
        if(e.registry.key() == Registries.ITEM) {
            ITEM_TAGS.forEach { (tags, blocks) ->
                e.addToTag(tags, *blocks.toTypedArray())
            }
        }
    }
}

fun Block.addTag(tag: ResourceLocation) = addTag(TagKey.create(Registries.BLOCK, tag))
fun Block.addTag(tag: TagKey<Block>) = TagManager.BLOCK_TAGS.getOrPut(tag, ::HashSet).add(this)

fun Item.addTag(tag: ResourceLocation) {
    val tagKey = TagKey.create(Registries.ITEM, tag)
    TagManager.ITEM_TAGS.getOrPut(tagKey, ::HashSet).add(this)
}

fun Block.addTool(type: ToolType, tier: Tier) {
    val tag = when (tier) {
        Tiers.WOOD -> TagKey.create(Registries.BLOCK, "needs_wood_tool".rl) // Only forge?
        Tiers.GOLD -> TagKey.create(Registries.BLOCK, "needs_gold_tool".rl) // Only forge?
        Tiers.STONE -> BlockTags.NEEDS_STONE_TOOL
        Tiers.IRON -> BlockTags.NEEDS_IRON_TOOL
        Tiers.DIAMOND -> BlockTags.NEEDS_DIAMOND_TOOL
        Tiers.NETHERITE -> TagKey.create(Registries.BLOCK, "needs_netherite_tool".rl) // Only forge?
        else -> error("Unknown tier: $tier")
    }
    addTag(tag)
    addTag(type.tag)
}

interface ToolType {
    val tag: TagKey<Block>
}

enum class ToolTypes(override val tag: TagKey<Block>) : ToolType {
    AXE(BlockTags.MINEABLE_WITH_AXE),
    HOE(BlockTags.MINEABLE_WITH_HOE),
    PICKAXE(BlockTags.MINEABLE_WITH_PICKAXE),
    SHOVEL(BlockTags.MINEABLE_WITH_SHOVEL)
}
