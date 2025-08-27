package ru.hollowhorizon.hc.api

import ru.hollowhorizon.hc.client.particles.ParticleSystem

interface ParticlesProvider {
    val system: ParticleSystem
}