package ru.hollowhorizon.hollowengine.client.particles.light

import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

fun interface LightProvider {
    fun query(pos: Vec3f): Int
}