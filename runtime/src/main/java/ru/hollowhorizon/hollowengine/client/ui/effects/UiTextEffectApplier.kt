package ru.hollowhorizon.hollowengine.client.ui.effects

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object UiTextEffectApplier {

    fun apply(
        effect: UiTextEffect,
        ctx: UiEffectContext,
    ): UiTextEffectResult {
        return when (effect) {
            is Shadow, is Outline -> UiTextEffectResult()
            is Glow -> applyGlow(effect, ctx)
            is Gradient -> applyGradient(effect, ctx)
            is Rainbow -> applyRainbow(effect, ctx)
            is Pulse -> applyPulse(effect, ctx)
            is Wave -> applyWave(effect, ctx)
            is Shake -> applyShake(effect, ctx)
            is Wiggle -> applyWiggle(effect, ctx)
            is Swing -> applySwing(effect, ctx)
            is Scroll -> applyScroll(effect, ctx)
            is Glitch -> applyGlitch(effect, ctx)
            else -> UiTextEffectResult()
        }
    }

    fun getLayerPasses(effect: UiTextEffect): List<UiTextEffectResult> {
        return when (effect) {
            is Shadow -> {
                val result = UiTextEffectResult(
                    offsetX = effect.offsetX,
                    offsetY = effect.offsetY,
                    colorOverride = effect.color,
                )
                listOf(result)
            }

            is Outline -> {
                val dirs = listOf(
                    -1f to -1f, 0f to -1f, 1f to -1f,
                    -1f to 0f, 1f to 0f,
                    -1f to 1f, 0f to 1f, 1f to 1f,
                )
                dirs.map { (dx, dy) ->
                    UiTextEffectResult(
                        offsetX = dx * effect.width,
                        offsetY = dy * effect.width,
                        colorOverride = effect.color,
                    )
                }
            }

            is Glow -> {
                (1..effect.quality).map { ring ->
                    val t = ring.toFloat() / effect.quality.toFloat()
                    val alpha = effect.color.alpha * (1f - t) * (1f - t)
                    UiTextEffectResult(
                        offsetX = 0f,
                        offsetY = 0f,
                        colorOverride = UiColor(
                            effect.color.red,
                            effect.color.green,
                            effect.color.blue,
                            alpha,
                        ),
                    )
                }
            }

            else -> emptyList()
        }
    }

    private fun applyGlow(effect: Glow, ctx: UiEffectContext): UiTextEffectResult {
        val t = ctx.charPos
        val alpha = effect.color.alpha * (1f - t) * (1f - t)
        return UiTextEffectResult(
            colorOverride = UiColor(
                effect.color.red,
                effect.color.green,
                effect.color.blue,
                alpha,
            ),
        )
    }

    private fun applyGradient(effect: Gradient, ctx: UiEffectContext): UiTextEffectResult {
        if (effect.colors.size < 2) return UiTextEffectResult()
        val t = if (effect.mode == GradientMode.HORIZONTAL) {
            ctx.charPos
        } else {
            (ctx.charIndex.toFloat() / ctx.totalChars.toFloat())
        }
        val shifted = (t + ctx.time * effect.speed + effect.phaseOffset).let { it - floor(it) }
        val segmentCount = effect.colors.size - 1
        val segment = (shifted * segmentCount).coerceIn(0f, segmentCount.toFloat())
        val segIndex = segment.toInt().coerceAtMost(segmentCount - 1)
        val localT = segment - segIndex
        val a = effect.colors[segIndex]
        val b = effect.colors[segIndex + 1]
        return UiTextEffectResult(
            colorOverride = UiColor(
                a.red + (b.red - a.red) * localT,
                a.green + (b.green - a.green) * localT,
                a.blue + (b.blue - a.blue) * localT,
                a.alpha + (b.alpha - a.alpha) * localT,
            ),
        )
    }

    private fun applyRainbow(effect: Rainbow, ctx: UiEffectContext): UiTextEffectResult {
        val hue = (ctx.charPos * effect.frequency + ctx.time * effect.speed + effect.phaseOffset) % 1f
        val color = hsvToRgb(hue, effect.saturation, effect.brightness)
        return UiTextEffectResult(colorOverride = color)
    }

    private fun applyPulse(effect: Pulse, ctx: UiEffectContext): UiTextEffectResult {
        val oscillation = (cos((ctx.charPos + ctx.time) * effect.frequency * Math.PI.toFloat() * 2f) + 1f) / 2f
        val alpha = effect.minAlpha + oscillation * effect.amplitude
        return UiTextEffectResult(alphaMultiplier = alpha.coerceIn(0f, 1f))
    }

    private fun applyWave(effect: Wave, ctx: UiEffectContext): UiTextEffectResult {
        val offsetY = ctx.sineWave(effect.amplitude, effect.frequency, effect.speed, effect.phaseOffset)
        return UiTextEffectResult(offsetY = offsetY)
    }

    private fun applyShake(effect: Shake, ctx: UiEffectContext): UiTextEffectResult {
        val intensity = abs(sin(ctx.time * effect.frequency * 0.5f))
        val offsetX = ctx.seededRandom(ctx.time + 0.3f) * effect.amplitude * intensity
        val offsetY = ctx.seededRandom(ctx.time + 0.7f) * effect.amplitude * intensity
        return UiTextEffectResult(offsetX = offsetX, offsetY = offsetY)
    }

    private fun applyWiggle(effect: Wiggle, ctx: UiEffectContext): UiTextEffectResult {
        val angle = effect.angleDegrees * (Math.PI.toFloat() / 180f)
        val oscillation = ctx.sineWave(effect.amplitude, effect.frequency, effect.speed, 0f)
        val offsetX = cos(angle) * oscillation
        val offsetY = sin(angle) * oscillation
        return UiTextEffectResult(offsetX = offsetX, offsetY = offsetY)
    }

    private fun applySwing(effect: Swing, ctx: UiEffectContext): UiTextEffectResult {
        val offsetY = ctx.sineWave(effect.amplitude, effect.frequency, effect.speed, 0f)
        return UiTextEffectResult(offsetY = offsetY)
    }

    private fun applyScroll(effect: Scroll, ctx: UiEffectContext): UiTextEffectResult {
        val totalWidth = ctx.runWidth
        val cycleWidth = totalWidth
        val offset = (ctx.time * effect.speed) % (cycleWidth + effect.pauseAtEnd * effect.speed)
        val effectiveOffset = if (offset > cycleWidth) {
            cycleWidth
        } else {
            offset
        }
        return UiTextEffectResult(offsetX = -effectiveOffset)
    }

    private fun applyGlitch(effect: Glitch, ctx: UiEffectContext): UiTextEffectResult {
        val trigger = ctx.seededRandom(effect.frequency, (ctx.time * 100).toInt()) * 0.5f + 0.5f
        val glitchActive = trigger > 0.92f
        if (!glitchActive) return UiTextEffectResult()
        val sliceShift = ctx.seededRandom(effect.frequency + 13f) * effect.intensity
        val offsetY = ctx.seededRandom(effect.frequency + 17f) * effect.intensity * 0.5f
        return UiTextEffectResult(offsetX = sliceShift, offsetY = offsetY)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): UiColor {
        val hue = h * 6f
        val sector = hue.toInt()
        val frac = hue - sector
        val p = v * (1f - s)
        val q = v * (1f - s * frac)
        val t = v * (1f - s * (1f - frac))
        return when (sector % 6) {
            0 -> UiColor(v, t, p, 1f)
            1 -> UiColor(q, v, p, 1f)
            2 -> UiColor(p, v, t, 1f)
            3 -> UiColor(p, q, v, 1f)
            4 -> UiColor(t, p, v, 1f)
            5 -> UiColor(v, p, q, 1f)
            else -> UiColor(v, t, p, 1f)
        }
    }
}
