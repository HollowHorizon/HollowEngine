package ru.hollowhorizon.hollowengine.api

import net.minecraft.client.multiplayer.ClientLevel
import ru.hollowhorizon.hollowengine.client.particles.ClientParticleSystems
import ru.hollowhorizon.hollowengine.client.particles.ParticleSystem

interface ParticlesProvider {
    val system: ParticleSystem
}

val ClientLevel.system: ParticleSystem
    get() = ClientParticleSystems.system(this)
