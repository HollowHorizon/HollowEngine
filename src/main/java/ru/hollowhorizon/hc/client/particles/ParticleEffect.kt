package ru.hollowhorizon.hc.client.particles

import net.minecraft.client.renderer.texture.AbstractTexture
import ru.hollowhorizon.hc.client.audio.Wave
import ru.hollowhorizon.hc.client.particles.file.ParticleComponents
import ru.hollowhorizon.hc.client.particles.file.BedrockParticleFile
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture

class ParticleEffect(
    val file: String,
    val identifier: String,
    val material: BedrockParticleFile.Material,
    val components: ParticleComponents,
    val curves: Map<String, BedrockParticleFile.Curve>,
    val events: Map<String, BedrockParticleFile.Event>,
    val texture: AbstractTexture?,
    val referencedEffects: Map<String, ParticleEffect>,
    val referencedSounds: Map<String, Wave>,
) {
    val renderPass = texture?.let { RenderPass(material, it) }

    data class RenderPass(val material: BedrockParticleFile.Material, val texture: AbstractTexture)

    companion object {
        fun fromFile(file: BedrockParticleFile) = ParticleEffect(
            file.particleEffect.description.identifier,
            file.particleEffect.description.identifier,
            file.particleEffect.description.basicRenderParameters.material,
            file.particleEffect.components,
            file.particleEffect.curves,
            file.particleEffect.events,
            file.particleEffect.description.basicRenderParameters.texture.rl.toTexture(),
            mapOf(), mapOf()
        )
    }
}
