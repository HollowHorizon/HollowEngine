package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import ru.hollowhorizon.hollowengine.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

var NpcEntity.model: String
    get() = this[AnimatedEntityCapability::class.java].model
    set(value) {
        this[AnimatedEntityCapability::class.java].model = value
    }

fun NpcEntity.resetModel() {
    this[AnimatedEntityCapability::class.java].model = "%NO_MODEL%"
}

val NpcEntity.textures get() = this[AnimatedEntityCapability::class.java].textures
var NpcEntity.transform
    get() = this[AnimatedEntityCapability::class.java].transform
    set(value) {
        this[AnimatedEntityCapability::class.java].transform = value
    }