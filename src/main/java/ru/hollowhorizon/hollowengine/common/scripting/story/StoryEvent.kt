package ru.hollowhorizon.hollowengine.common.scripting.story

import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.scripting.Suspendable
import ru.hollowhorizon.hollowengine.scripting.nodes.Node
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "se.kts",
    compilationConfiguration = StoryEventConfiguration::class
)
class StoryEvent {
    lateinit var script: Node // Плагин для компилятора сам инициализирует эту переменную на основе данных из скрипта
}

class StoryEventConfiguration : AbstractHollowScriptConfiguration({

})

@Suspendable
fun example() {
    val player = currentServer.playerList.players.random()

    val vitalik = npc(pos = player.position())


}

fun getNode() = Class.forName("ru.hollowhorizon.hollowengine.common.scripting.story.StoryEventKt")
    .declaredMethods.first { it.name == "example" }.invoke(null) as Node

fun main() {
    val node = getNode()

    println(node)
}