package ru.hollowhorizon.hollowengine.common.events.registry

import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import ru.hollowhorizon.hollowengine.common.events.Event

class RegisterParticlesEvent(val particleEngine: ParticleEngine): Event {
    fun <T: ParticleOptions> registerSpecial(type: ParticleType<T>, provider: ParticleProvider<T>) {
        particleEngine.register(type, provider)
    }

    fun <T: ParticleOptions> registerSprite(type: ParticleType<T>, provider: ParticleProvider.Sprite<T>) {
        particleEngine.register(type, provider)
    }

    fun <T: ParticleOptions> registerSpriteSet(type: ParticleType<T>, provider: ParticleEngine.SpriteParticleRegistration<T>) {
        particleEngine.register(type, provider)
    }
}