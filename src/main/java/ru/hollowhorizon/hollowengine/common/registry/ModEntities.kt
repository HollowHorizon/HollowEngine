package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.entities.SeatEntity

object ModEntities : HollowRegistry(HollowEngine.MODID) {
    val NPC_ENTITY: EntityType<NpcEntity> by register("npc_entity") {
        EntityType.Builder.of(::NpcEntity, MobCategory.CREATURE).sized(0.6f, 1.8f).build("npc_entity")
    }

    val SEAT: EntityType<SeatEntity> by register("seat") {
        EntityType.Builder.of(::SeatEntity, MobCategory.CREATURE).sized(0.0f, 0.0f).build("seat")
    }
}

@SubscribeEvent
fun onRegisterAttributes(event: RegisterEntityAttributesEvent) {
    event.register(ModEntities.NPC_ENTITY, NpcEntity.createAttributes().build())
    event.register(ModEntities.SEAT, LivingEntity.createLivingAttributes().build())
}

fun main() {
    val hello = true
    val world by lazy { false }
    println("Hello World")
}