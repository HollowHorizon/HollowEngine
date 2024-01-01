package ru.hollowhorizon.hollowengine.common.particles

import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.particles.GenericData
import ru.hollowhorizon.hollowengine.client.particles.ParticleColor
import ru.hollowhorizon.hollowengine.common.registry.ModParticles

fun main() {
    HollowParticleBuilder.create(ModParticles.HOLLOW_PARTICLE)
        .setTransparencyData(GenericData(0.02f, 0.05f, 0f))
        .setSpinData(GenericData(0.1f, 0.4f, 0f, 1.0f, Interpolation.QUINT_OUT, Interpolation.SINE_IN))
        .setScaleData(GenericData(0.15f, 0.4f, 0.35f, 1.0f, Interpolation.QUINT_OUT, Interpolation.SINE_IN))
        .setColorData(ParticleColor(66 / 255f, 135 / 255f, 245 / 255f, 145 / 255f, 49 / 255f, 17 / 255f))
        .setLifetime(250)
        .enableNoClip()
        .setRandomOffset(0.05, 0.05)
        .setRandomMotion(0.05)
}