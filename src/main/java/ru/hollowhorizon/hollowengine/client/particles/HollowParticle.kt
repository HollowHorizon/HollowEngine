package ru.hollowhorizon.hollowengine.client.particles

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleEngine.MutableSpriteSet
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.util.FastColor

import net.minecraft.util.Mth
import java.awt.Color


class HollowParticle(
    level: ClientLevel,
    private val options: HollowParticleOptions,
    private val spriteSet: SpriteSet,
    x: Double, y: Double, z: Double,
    mX: Double, mY: Double, mZ: Double
) : TextureSheetParticle(level, x, y, z, mX, mY, mZ) {
    private var reachedPositiveAlpha = false
    private var reachedPositiveScale = false
    private var hsv1 = FloatArray(3)
    private var hsv2 = FloatArray(3)

    init {
        options.apply {
            Color.RGBtoHSB(
                (255 * 1.0f.coerceAtMost(colorData.r1)).toInt(),
                (255 * 1.0f.coerceAtMost(colorData.g1)).toInt(),
                (255 * 1.0f.coerceAtMost(colorData.b1)).toInt(),
                hsv1
            )
            Color.RGBtoHSB(
                (255 * 1.0f.coerceAtMost(colorData.r2)).toInt(),
                (255 * 1.0f.coerceAtMost(colorData.g2)).toInt(),
                (255 * 1.0f.coerceAtMost(colorData.b2)).toInt(),
                hsv2
            )

            when (spritePicker) {
                SpritePicker.RANDOM_SPRITE -> pickSprite(spriteSet)
                SpritePicker.FIRST_INDEX, SpritePicker.WITH_AGE -> pickSprite(0)
                SpritePicker.LAST_INDEX -> pickSprite(-1)
            }

            hasPhysics = !noClip
            this@HollowParticle.gravity = gravity
            this@HollowParticle.lifetime = lifetime
        }
    }

    override fun tick() {
        super.tick()

        options.apply {
            var shouldAttemptRemoval = discardType == DiscardType.INVISIBLE
            if (
                discardType == DiscardType.ENDING_CURVE_INVISIBLE &&
                scaleData.getProgress(age, lifetime) > 0.5f || transparencyData.getProgress(age, lifetime) > 0.5f
            ) shouldAttemptRemoval = true

            if (shouldAttemptRemoval) {
                if (reachedPositiveAlpha && alpha <= 0 || reachedPositiveScale && quadSize <= 0) {
                    remove()
                    return
                }
            }

            if (spritePicker == SpritePicker.WITH_AGE) {
                setSpriteFromAge(spriteSet)
            }
            pickColor(colorData.colorCurveEasing.function(colorData.getProgress(age, lifetime)))

            quadSize = scaleData.getValue(age, lifetime)
            alpha = transparencyData.getValue(age, lifetime)
            oRoll = roll
            roll += spinData.getValue(age, lifetime)
        }

        if (!reachedPositiveAlpha && alpha > 0) reachedPositiveAlpha = true
        if (!reachedPositiveScale && quadSize > 0) reachedPositiveScale = true
    }

    private fun pickColor(colorCoeff: Float) {
        val h = Mth.rotLerp(colorCoeff, 360f * hsv1[0], 360f * hsv2[0]) / 360f
        val s = Mth.lerp(colorCoeff, hsv1[1], hsv2[1])
        val v = Mth.lerp(colorCoeff, hsv1[2], hsv2[2])
        val packed: Int = Color.HSBtoRGB(h, s, v)
        val r = FastColor.ARGB32.red(packed) / 255.0f
        val g = FastColor.ARGB32.green(packed) / 255.0f
        val b = FastColor.ARGB32.blue(packed) / 255.0f
        setColor(r, g, b)
    }

    private fun pickSprite(spriteIndex: Int) {
        val set = spriteSet as? MutableSpriteSet ?: return

        if (spriteIndex == -1) setSprite(set.sprites.last())
        if (spriteIndex < set.sprites.size && spriteIndex >= 0) setSprite(set.sprites[spriteIndex])
    }

    override fun getRenderType() = HollowParticleRenderType.apply {
        this.texture = sprite.atlas().location()
    }
}