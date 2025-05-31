package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.rl

fun execute(command: String): Int {
    val src = currentServer.createCommandSourceStack()
        .withPermission(4)
        .withSuppressedOutput()

    return currentServer.commands.performPrefixedCommand(src, command)
}

fun MinecraftServer.getLevel(location: String): ServerLevel =
    getLevel(levelKeys().find { it.location() == location.rl } ?: error("Dimension $location not found!"))
        ?: error("Dimension $location not loaded!")