package ru.hollowhorizon.hollowengine.story.extensions

import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.TickEvent
import ru.hollowhorizon.hollowengine.story.StoryEvent
import ru.hollowhorizon.hollowengine.story.waitForgeEvent

/**
 * Если в параметрах ничего, то принимается любое сообщение, если есть строки, то допускаются только они, другие сообщения игнорируются
 */
fun StoryEvent.input(vararg values: String): String {
    var input = ""
    waitForgeEvent<ServerChatEvent> { event ->
        input = event.message

        if(values.isEmpty()) return@waitForgeEvent true

        return@waitForgeEvent event.message in values

    }

    return input
}

fun StoryEvent.execute(command: String): Int {
    val server = this.world.level.server
    val src = server.createCommandSourceStack()

    if(team.getHost().isOnline()) src.withEntity(team.getHost().mcPlayer!!)

    return server.commands.performCommand(src.withPermission(4), command)
}

fun StoryEvent.waitLocation(x: Int, y: Int, z: Int, radius: Int, inverse: Boolean = false) {
    waitForgeEvent<TickEvent.ServerTickEvent> { _ ->
        var result = false
        team.getAllOnline().forEach {
            val dist = it.mcPlayer!!.distanceToSqr(x.toDouble(), y.toDouble(), z.toDouble())

            if(!inverse) {
                if(dist <= radius * radius) result = true
            } else {
                if(dist >= radius * radius) result = true
            }
        }
        return@waitForgeEvent result
    }
}