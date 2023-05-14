package ru.hollowhorizon.hollowengine.story.extensions

import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.TickEvent
import ru.hollowhorizon.hollowengine.story.StoryEvent
import ru.hollowhorizon.hollowengine.story.waitForgeEvent

fun StoryEvent.input(vararg values: String): String {
    var input = ""
    waitForgeEvent<ServerChatEvent> { event ->
        input = event.message

        if(values.isEmpty()) return@waitForgeEvent true

        return@waitForgeEvent event.message in values

    }

    return input
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