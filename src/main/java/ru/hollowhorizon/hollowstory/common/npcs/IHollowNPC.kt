package ru.hollowhorizon.hollowstory.common.npcs

import net.minecraft.entity.LivingEntity
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity

interface IHollowNPC {
    val npcEntity: NPCEntity


    fun stopFollow()

    fun attackTarget(entity: LivingEntity, damage: Float) {

    }

    fun runAway(time: Float) {

    }
}
