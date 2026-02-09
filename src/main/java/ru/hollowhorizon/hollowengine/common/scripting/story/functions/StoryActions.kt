package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.rl

fun execute(command: String): Int {
    val src = currentServer.createCommandSourceStack()
        .withPermission(4)
        .withSuppressedOutput()

    //? if > 1.20.1 {
    /*currentServer.commands.performPrefixedCommand(src, command)
    return 0
    *///?} else {
    return currentServer.commands.performPrefixedCommand(src, command)
    //?}
}

fun MinecraftServer.getLevel(location: String): ServerLevel =
    getLevel(levelKeys().find { it.location() == location.rl } ?: error("Dimension $location not found!"))
        ?: error("Dimension $location not loaded!")