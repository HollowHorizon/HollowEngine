package ru.hollowhorizon.hollowengine.client.ui.render

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.DrawParticlesCommand
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.particles.UiParticleAnimation
import kotlin.math.min

/** UV clipping happens before the node transform, so rotated/scaled areas keep their exact bounds. */
internal fun particlePlacement(
    x: Float, y: Float, size: Float, width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float,
): ImagePlacement? {
    if (!size.isFinite() || size <= 0f) return null
    val left = x - size * 0.5f
    val top = y - size * 0.5f
    val clippedLeft = left.coerceAtLeast(0f)
    val clippedTop = top.coerceAtLeast(0f)
    val right = (left + size).coerceAtMost(width)
    val bottom = (top + size).coerceAtMost(height)
    if (right <= clippedLeft || bottom <= clippedTop) return null
    return ImagePlacement(
        clippedLeft, clippedTop, right - clippedLeft, bottom - clippedTop,
        u0 + (u1 - u0) * ((clippedLeft - left) / size),
        v0 + (v1 - v0) * ((clippedTop - top) / size),
        u0 + (u1 - u0) * ((right - left) / size),
        v0 + (v1 - v0) * ((bottom - top) / size),
    )
}

internal fun appendParticleQuads(
    command: DrawParticlesCommand,
    transform: UiMatrix4,
    batches: MutableMap<ResourceLocation, MutableList<UiTexturedQuad>>,
) {
    val system = command.system
    system.prepare(command.rect.width, command.rect.height)
    if (system.particleCount == 0) return
    // Resolve once per emitter, not per particle. The lists are vanilla-owned and rebound on reload;
    // never retain atlas sprites across frames, even for particles which were already alive.
    val engine = Minecraft.getInstance().particleEngine
    val sprites = arrayOfNulls<List<TextureAtlasSprite>>(system.emitters.size)
    system.emitters.forEachIndexed { index, emitter ->
        sprites[index] = engine.spriteSets[emitter.location]?.sprites
    }
    val interpolation = system.interpolation
    var quads: MutableList<UiTexturedQuad>? = null
    system.forEachParticle { particle ->
        val frames = sprites[particle.emitterIndex]
        if (frames.isNullOrEmpty()) return@forEachParticle
        val emitter = system.emitters[particle.emitterIndex]
        val age = particle.previousAge + (particle.age - particle.previousAge) * interpolation
        val progress = (age / particle.lifetime).coerceIn(0f, 1f)
        val frameProgress = when (emitter.animation) {
            UiParticleAnimation.OVER_LIFETIME -> progress
            UiParticleAnimation.RANDOM_FRAME -> particle.randomFrame
        }
        val sprite = frames[min((frameProgress * frames.size).toInt(), frames.lastIndex)]
        val opacity = emitter.alpha.sample(progress) * command.opacity
        if (!opacity.isFinite() || opacity <= 0f || particle.color.alpha <= 0f) return@forEachParticle
        val placement = particlePlacement(
            particle.previousX + (particle.x - particle.previousX) * interpolation,
            particle.previousY + (particle.y - particle.previousY) * interpolation,
            particle.size * emitter.scale.sample(progress), command.rect.width, command.rect.height,
            sprite.u0, sprite.v0, sprite.u1, sprite.v1,
        ) ?: return@forEachParticle
        val destination = quads ?: batches.getOrPut(TextureAtlas.LOCATION_PARTICLES) { ArrayList() }.also { quads = it }
        destination += UiTexturedQuad(
            width = placement.width,
            height = placement.height,
            transform = transform,
            opacity = opacity.coerceAtMost(1f),
            tint = particle.color,
            region = placement,
        )
    }
}
