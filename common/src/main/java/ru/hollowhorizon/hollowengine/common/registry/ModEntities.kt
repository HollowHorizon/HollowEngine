package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.RegistryObject
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

object ModEntities : HollowRegistry() {
    val NPC_ENTITY: RegistryObject<EntityType<NPCEntity>> by register(
        ResourceLocation.fromNamespaceAndPath(HollowEngine.MODID, "npc_entity")
    ) {
        EntityType.Builder.of(::NPCEntity, MobCategory.CREATURE).sized(0.6f, 1.8f).build("npc_entity")
    }
}

@SubscribeEvent
fun onRegisterAttributes(event: RegisterEntityAttributesEvent) {
    event.register(ModEntities.NPC_ENTITY.get(), Mob.createMobAttributes().build())
}