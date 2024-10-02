package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun example() {
    val player = currentServer.playerList.players.first()
    val npc = npc(pos = pos(95, 69, -70))

    while (npc.distanceTo(player) > 1.5) {
        npc.navigation.moveTo(player, 1.0)
    }

    npc say "Привет!"

    var ticks = 20
    while (ticks > 0) {
        ticks-- // Циклы выполняются по-тиково, не чаще 1 итерации в тик
    }

    npc say "Как дела?"
}

private infix fun NPCEntity.say(text: String) {
    server?.playerList?.players?.forEach {
        it.sendSystemMessage("[${this.name.string}] $text".literal)
    }
}

