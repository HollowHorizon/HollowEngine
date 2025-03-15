package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attributes
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterEntityAttributesEvent
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.entities.SeatEntity

object ModEntities : HollowRegistry(HollowEngine.MODID) {
    val NPC_ENTITY: EntityType<NPCEntity> by register("npc_entity") {
        EntityType.Builder.of(::NPCEntity, MobCategory.CREATURE).sized(0.6f, 1.8f).build("npc_entity")
    }

    val SEAT: EntityType<SeatEntity> by register("seat") {
        EntityType.Builder.of(::SeatEntity, MobCategory.CREATURE).sized(0.0f, 0.0f).build("seat")
    }
}

@SubscribeEvent
fun onRegisterAttributes(event: RegisterEntityAttributesEvent) {
    event.register(ModEntities.NPC_ENTITY, Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).build())
}

fun main() {
    val hello = true
    val world by lazy { false }
    println("Hello World")
}