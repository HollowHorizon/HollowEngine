package ru.hollowhorizon.hollowengine.common.events.registry

//? if < 1.21 {

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootDataId
import net.minecraft.world.level.storage.loot.LootDataType
import net.minecraft.world.level.storage.loot.LootTable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

class RegisterLootEvent(private val elements: MutableMap<LootDataId<*>, *>) : Event {

    fun addLoot(block: Block, table: LootTable) {
        val blockId = BuiltInRegistries.BLOCK.getKey(block)
        val location = LootDataId(LootDataType.TABLE, ResourceLocation(blockId.namespace, "blocks/${blockId.path}"))
        elements[location] = JavaHacks.forceCast(table)
    }

    fun addLoot(entityType: EntityType<*>, table: LootTable) {
        val blockId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
        val location = LootDataId(LootDataType.TABLE, ResourceLocation(blockId.namespace, "entity/${blockId.path}"))
        elements[location] = JavaHacks.forceCast(table)
    }

    fun removeLoot(block: Block) {
        val blockId = BuiltInRegistries.BLOCK.getKey(block)
        val location = ResourceLocation(blockId.namespace, "blocks/${blockId.path}")
        elements.keys.removeIf { it.location == location }
    }

    fun removeLoot(block: EntityType<*>) {
        val blockId = BuiltInRegistries.ENTITY_TYPE.getKey(block)
        val location = ResourceLocation(blockId.namespace, "entity/${blockId.path}")
        elements.keys.removeIf { it.location == location }
    }
}

//?}