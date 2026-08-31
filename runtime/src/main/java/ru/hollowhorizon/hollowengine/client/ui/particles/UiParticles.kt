package ru.hollowhorizon.hollowengine.client.ui.particles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.drawBehind

/** Advances the render clock without invalidating composition, styles or layout every frame. */
@Composable
fun rememberUiParticleSystem(
    vararg emitters: UiParticleEmitter,
    maxParticles: Int = 512,
    seed: Int = 0,
): UiParticleSystem {
    val definitions = emitters.toList()
    val system = remember(definitions, maxParticles, seed) {
        UiParticleSystem(definitions, maxParticles = maxParticles, seed = seed)
    }
    LaunchedEffect(system) {
        while (isActive) withFrameNanos { system.frameTimeNanos = it }
    }
    return system
}

/** Draws particles above the styled background and below the node's content. */
fun Modifier.particleBackground(system: UiParticleSystem): Modifier =
    drawBehind(system) { drawParticles(system) }
