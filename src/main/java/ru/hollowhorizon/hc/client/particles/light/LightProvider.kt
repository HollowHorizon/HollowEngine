package ru.hollowhorizon.hc.client.particles.light

import de.fabmax.kool.math.Vec3f

fun interface LightProvider {
    fun query(pos: Vec3f): Int
}