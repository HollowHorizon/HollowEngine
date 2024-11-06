package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.rl

fun execute(command: String): Int {
    val src = currentServer.createCommandSourceStack()
        .withPermission(4)
        .withSuppressedOutput()

    return currentServer.commands.performPrefixedCommand(src, command)
}

fun MinecraftServer.getLevel(location: String) =
    getLevel(levelKeys().find { it.location() == location.rl } ?: error("Dimension $location not found!"))