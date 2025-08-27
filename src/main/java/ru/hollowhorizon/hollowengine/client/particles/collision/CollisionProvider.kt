package ru.hollowhorizon.hollowengine.client.particles.collision

import de.fabmax.kool.math.Vec3f

fun interface CollisionProvider {
    fun query(pos: Vec3f, size: Float, offset: Vec3f): Pair<Vec3f, Vec3f>?

    object None : CollisionProvider {
        override fun query(pos: Vec3f, size: Float, offset: Vec3f): Pair<Vec3f, Vec3f>? = null
    }
}