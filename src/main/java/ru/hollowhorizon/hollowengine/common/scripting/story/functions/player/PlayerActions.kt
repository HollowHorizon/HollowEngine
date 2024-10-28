package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.compiler.suspendable.await
import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun Player.waitPos(pos: Vec3, radius: Float = 1f, inverse: Boolean = false) {
    if (inverse) {
        await(distanceToSqr(pos) >= radius * radius)
    } else {
        await(distanceToSqr(pos) <= radius * radius)
    }
}