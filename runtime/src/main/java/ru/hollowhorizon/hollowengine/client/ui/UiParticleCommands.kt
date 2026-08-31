package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.particles.UiParticleSystem
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain

/** One command per particle area; its sprites join the renderer's existing atlas image batches. */
data class DrawParticlesCommand(
    override val node: UiNode,
    val rect: UiRect,
    val system: UiParticleSystem,
    val opacity: Float,
    val transform: UiMatrix4,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val phase: UiRenderPhase = UiRenderPhase.CONTENT,
) : UiRenderCommand
