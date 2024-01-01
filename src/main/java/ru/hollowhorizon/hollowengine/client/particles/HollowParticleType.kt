package ru.hollowhorizon.hollowengine.client.particles

import com.mojang.serialization.Codec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.ParticleType

class HollowParticleType: ParticleType<HollowParticleOptions>(false, HollowParticleOptions.DESERIALIZER) {
    override fun codec(): Codec<HollowParticleOptions> = HollowParticleOptions.codecFor(this)

    class Factory(val sprite: SpriteSet): ParticleProvider<HollowParticleOptions> {
        override fun createParticle(
            options: HollowParticleOptions,
            pLevel: ClientLevel,
            pX: Double,
            pY: Double,
            pZ: Double,
            pXSpeed: Double,
            pYSpeed: Double,
            pZSpeed: Double
        ) = HollowParticle(pLevel, options, sprite, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed)

    }
}