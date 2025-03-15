package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.utils.get

var Player.model: String
    get() = this[AnimatedEntityCapability::class.java].model
    set(value) {
        this[AnimatedEntityCapability::class.java].model = value
    }

fun Player.resetModel() {
    this[AnimatedEntityCapability::class.java].model = "%NO_MODEL%"
}

val Player.textures get() = this[AnimatedEntityCapability::class.java].textures
val Player.animations get() = this[AnimatedEntityCapability::class.java].animations
var Player.transform
    get() = this[AnimatedEntityCapability::class.java].transform
    set(value) {
        this[AnimatedEntityCapability::class.java].transform = value
    }
var Player.switchHeadRot
    get() = this[AnimatedEntityCapability::class.java].switchHeadRot
    set(value) {
        this[AnimatedEntityCapability::class.java].switchHeadRot = value
    }