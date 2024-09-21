package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlinx.coroutines.delay
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.common.coroutines.suspendBy
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.scripting.Suspendable
import kotlin.math.sqrt

fun NPCEntity.moveTo(pos: Vec3, endDistance: Float = 1f, speed: Double = 1.0) {
    while (sqrt(distanceToSqr(pos)) > endDistance) {
        //? if >=1.21 {
        navigation.moveTo(pos.x, pos.y, pos.z, 0, speed)
        //?} else {
        /*navigation.moveTo(pos.x, pos.y, pos.z, speed)
        *///?}
    }

}

suspend infix fun NPCEntity.moveToBlock(block: Vec3) {
    navigation.moveTo(block.x, block.y, block.z, 0, 0.0)
    suspendBy { distanceToSqr(block) > 1 }
}

suspend fun example() {
    val npc = npc(pos= pos(0, 0, 0))

    npc say "Привет!"

    delay(1000)

    npc moveToBlock pos(0, 0, 0)

    delay(500L)

    npc say "Как дела?"
}

fun NPCEntity.lookAt(pos: Vec3) {
    lookControl.setLookAt(pos.x, pos.y, pos.z)
}

fun NPCEntity.lookAt(pos: Entity) {
    lookControl.setLookAt(pos.eyePosition.x, pos.eyePosition.y, pos.eyePosition.z)
}

infix fun NPCEntity.say(text: String) {
    server?.playerList?.players?.forEach {
        it.sendToast(text.literal)
    }
}

