package ru.hollowhorizon.hollowengine.client.particles.collision

import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

fun interface CollisionProvider {
    fun query(pos: Vec3f, size: Float, offset: Vec3f): Pair<Vec3f, Vec3f>?

}