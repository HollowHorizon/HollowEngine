package ru.hollowhorizon.hollowengine.common.exceptions

import net.minecraft.world.entity.player.Player
import java.io.Serial
import java.util.*

class StoryPlayerNotFoundException(msg: String) : StoryEventException(msg) {
    constructor(uuid: UUID) : this("Player with uuid $uuid not found")

    constructor(name: Player) : this("Player with name ${name.name} not found")

    companion object {
        @Serial
        private const val serialVersionUID = -30427L
    }
}

class StoryHostNotFoundException(message: String) : StoryEventException(message) {
    constructor() : this("Host not found")

    companion object {
        @Serial
        private const val serialVersionUID = -8355L
    }
}