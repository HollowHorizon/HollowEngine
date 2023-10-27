package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraftforge.registries.RegistryObject
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.ObjectConfig
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

object ModEntities : HollowRegistry() {

    val NPC_ENTITY: RegistryObject<EntityType<NPCEntity>> by register(
        ObjectConfig(
            name = "npc_entity",
        )
    ) {
        EntityType.Builder.of(
            ::NPCEntity,
            MobCategory.CREATURE
        ).sized(0.6f, 1.8f).build("npc_entity")
    }
}
