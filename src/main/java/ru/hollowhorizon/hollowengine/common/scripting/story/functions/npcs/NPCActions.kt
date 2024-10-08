package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun NPCEntity.moveTo(entity: Entity, dist: Double = 1.5, speed: Double = 1.0) {
    while (distanceTo(entity) > dist) {
        navigation.moveTo(entity.x, entity.y, entity.z, 0, speed)
    }
    navigation.stop()
}

@Suspendable
fun wait(time: Int) {
    var ticks = time
    while (ticks > 0) {
        ticks-- // Циклы выполняются по-тиково, не чаще 1 итерации в тик
    }
}

@Suspendable
fun script() {
    val player = currentServer.playerList.players.first()
    val npc = npc(pos = pos(95, 69, -70))

    npc.moveTo(player)
    npc say "Привет!"
    wait(2.sec)
    npc say "Как дела?"
}

val Number.sec get() = (this.toFloat() * 20).toInt()

private infix fun NPCEntity.say(text: String) {
    server?.playerList?.players?.forEach {
        it.sendSystemMessage("[${this.name.string}] $text".literal)
    }
}

