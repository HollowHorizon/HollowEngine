package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

var NPCEntity.model: String
    get() = this[AnimatedEntityCapability::class.java].model
    set(value) {
        this[AnimatedEntityCapability::class.java].model = value
    }

fun NPCEntity.resetModel() {
    this[AnimatedEntityCapability::class.java].model = "%NO_MODEL%"
}

val NPCEntity.textures get() = this[AnimatedEntityCapability::class.java].textures
val NPCEntity.animations get() = this[AnimatedEntityCapability::class.java].animations
var NPCEntity.transform get() = this[AnimatedEntityCapability::class.java].transform
    set(value) {
        this[AnimatedEntityCapability::class.java].transform = value
    }
var NPCEntity.switchHeadRot get() = this[AnimatedEntityCapability::class.java].switchHeadRot
    set(value) {
        this[AnimatedEntityCapability::class.java].switchHeadRot = value
    }