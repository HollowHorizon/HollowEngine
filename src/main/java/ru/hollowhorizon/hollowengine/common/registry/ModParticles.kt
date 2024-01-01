package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.client.particle.SpriteSet
import net.minecraftforge.client.event.RegisterParticleProvidersEvent
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.client.particles.HollowParticleType
import ru.hollowhorizon.hollowengine.common.items.*

object ModParticles : HollowRegistry() {
    val HOLLOW_PARTICLE by register("hollow_particle", ::HollowParticleType)

    fun onRegisterParticles(event: RegisterParticleProvidersEvent) {
        event.register(HOLLOW_PARTICLE.get()) { set: SpriteSet -> HollowParticleType.Factory(set)}
    }
}