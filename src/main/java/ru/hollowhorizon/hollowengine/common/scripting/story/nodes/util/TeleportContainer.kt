package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class TeleportContainer {
    var pos: Vec3 = Vec3(0.0, 0.0, 0.0)
    var vec: Vec2 = Vec2(0F, 0F)
    var world: String = "overworld"
}