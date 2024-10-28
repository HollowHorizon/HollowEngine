package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import ru.hollowhorizon.hc.client.utils.currentServer

fun execute(command: String): Int {
    val src = currentServer.createCommandSourceStack()
        .withPermission(4)
        .withSuppressedOutput()

    return currentServer.commands.performPrefixedCommand(src, command)
}