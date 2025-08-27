package ru.hollowhorizon.hollowengine.api

import ru.hollowhorizon.hollowengine.client.particles.ParticleSystem

interface ParticlesProvider {
    val system: ParticleSystem
}