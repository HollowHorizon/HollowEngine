package ru.hollowhorizon.hollowengine.common.exceptions

import net.minecraft.entity.player.PlayerEntity
import java.util.*

class StoryPlayerNotFoundException(msg: String) : StoryEventException(msg) {
    constructor(uuid: UUID) : this("Player with uuid $uuid not found")

    constructor(name: PlayerEntity) : this("Player with name ${name.name} not found")
}

class StoryHostNotFoundException(message: String) : StoryEventException(message) {
    constructor() : this("Host not found")
}