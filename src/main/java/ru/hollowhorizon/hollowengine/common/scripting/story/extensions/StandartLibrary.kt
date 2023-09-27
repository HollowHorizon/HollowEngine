package ru.hollowhorizon.hollowengine.common.scripting.story.extensions

import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.TickEvent
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.network.OpenChatPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.waitForgeEvent

/**
 * Если в параметрах ничего, то принимается любое сообщение, если есть строки, то допускаются только они, другие сообщения игнорируются
 */
fun StoryEvent.input(vararg values: String, onlyHostMode: Boolean = false): String {
    var input = ""

    if(team.getHost().isOnline()) OpenChatPacket().send("", team.getHost().mcPlayer!!)
    else if(!onlyHostMode) OpenChatPacket().send("", *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())

    fun canChoice(player: ServerPlayer): Boolean {
        return if(onlyHostMode) team.isHost(player) else team.isFromTeam(player)
    }

    waitForgeEvent<ServerChatEvent> { event ->
        input = event.message

        return@waitForgeEvent (values.isEmpty() || values.any { it.equals(input, true) }) || !canChoice(event.player)

    }

    return input
}

fun StoryEvent.pos(x: Int, y: Int, z: Int) = BlockPos(x, y, z)

fun StoryEvent.execute(command: String): Int {
    val server = this.world.level.server
    val src = server.createCommandSourceStack()

    if(team.getHost().isOnline()) src.withEntity(team.getHost().mcPlayer!!)

    // server.commands.performPrefixedCommand(src.withPermission(4), command)

    return server.commands.performCommand(src.withPermission(Commands.LEVEL_OWNERS), command)
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