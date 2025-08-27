package ru.hollowhorizon.hollowengine.common.events.entity

import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event

class BabySpawnEvent(
    val parentA: Mob,
    val parentB: Mob?,
    var child: AgeableMob?
): Event, Cancelable {
    val causedByPlayer: Player

    init {
        var caused: Player? = null
        if (parentA is Animal)
            caused = parentA.loveCause

        if ((caused as Any?) == null && parentB is Animal)
            caused = parentB.loveCause

        causedByPlayer = caused!!
    }

    override var isCanceled: Boolean = false
}