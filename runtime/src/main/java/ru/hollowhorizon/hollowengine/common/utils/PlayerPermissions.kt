package ru.hollowhorizon.hollowengine.common.utils

object PlayerPermissions {
    const val ALL = 0 // All players
    const val MODERATOR = 1 // Player can bypass spawn protection.
    const val GAMEMASTER = 2 // Player can use more commands and command blocks.
    const val ADMINISTRATOR = 3 // Player or executor can use commands related to multiplayer management.
    const val OWNER = 4 // Player or executor can use all commands, including commands related to server management.
}