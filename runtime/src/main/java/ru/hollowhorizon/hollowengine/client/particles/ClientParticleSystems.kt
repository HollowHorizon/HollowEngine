package ru.hollowhorizon.hollowengine.client.particles

import net.minecraft.client.multiplayer.ClientLevel
import java.util.*

object ClientParticleSystems {
    private val systems = Collections.synchronizedMap(WeakHashMap<ClientLevel, ParticleSystem>())

    fun system(level: ClientLevel): ParticleSystem =
        systems.computeIfAbsent(level) { ParticleSystem.create(it) }
}
