package ru.hollowhorizon.hollowengine.client.ui.particles

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.HollowUiComposition
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiParticleSystemTest {
    @Test
    fun `compose clock advances without recomposition and stops when disposed`() {
        val definition = emitter(60f)
        lateinit var system: UiParticleSystem
        var compositions = 0
        val composition = HollowUiComposition()
        try {
            composition.setContent {
                compositions++
                system = rememberUiParticleSystem(definition)
                Box(modifier = Modifier.particleBackground(system))
            }
            repeat(10) { index ->
                composition.frameRoot(index * 20_000_000L)
                system.prepare(100f, 80f)
            }
            assertEquals(1, compositions)
            assertTrue(system.particleCount > 0)
        } finally {
            composition.close()
        }
        val time = system.frameTimeNanos
        composition.applyPendingChanges(1_000_000_000L)
        assertEquals(time, system.frameTimeNanos)
    }

    private fun emitter(rate: Float = 12f, initial: Int = 0) = UiParticleEmitter(
        particle = "minecraft:smoke", rate = rate, initialCount = initial,
        spawn = { area ->
            x = area.random.nextFloat() * area.width
            y = area.height
            velocityY = -10f
            lifetime = 10f
        },
    )

    private fun UiParticleSystem.snapshot(): List<List<Float>> = buildList {
        forEachParticle { add(listOf(it.x, it.y, it.age, it.lifetime, it.velocityY)) }
    }

    @Test
    fun `simulation is independent of rendering frequency`() {
        val slow = UiParticleSystem(listOf(emitter()), seed = 42)
        val fast = UiParticleSystem(listOf(emitter()), seed = 42)
        repeat(30) { slow.advance(1.0 / 30.0, 100f, 80f) }
        repeat(144) { fast.advance(1.0 / 144.0, 100f, 80f) }
        assertEquals(12, slow.particleCount)
        assertEquals(slow.snapshot(), fast.snapshot())
    }

    @Test
    fun `capacity drops excess emission without accumulating a later burst`() {
        val system = UiParticleSystem(listOf(emitter(600f).copy(spawn = { lifetime = 0.05f })), maxParticles = 3)
        repeat(120) {
            system.advance(1.0 / 60.0, 100f, 80f)
            assertTrue(system.particleCount <= 3)
        }
        system.emitting = false
        repeat(10) { system.advance(1.0 / 60.0, 100f, 80f) }
        assertEquals(0, system.particleCount)
        system.emitting = true
        system.advance(1.0 / 60.0, 100f, 80f)
        assertEquals(3, system.particleCount)
    }

    @Test
    fun `long hidden interval has bounded catch up and timestamp is consumed only once`() {
        val system = UiParticleSystem(listOf(emitter(60f)))
        system.frameTimeNanos = 0L
        system.prepare(100f, 80f)
        system.frameTimeNanos = 60_000_000_000L
        system.prepare(100f, 80f)
        assertEquals(15, system.particleCount)
        val snapshot = system.snapshot()
        system.prepare(100f, 80f)
        assertEquals(snapshot, system.snapshot())
    }

    @Test
    fun `paused clocks do not catch up on resume and stopping emission lets particles expire`() {
        val system = UiParticleSystem(listOf(emitter(0f, 2).copy(spawn = { lifetime = 0.1f })))
        system.frameTimeNanos = 0L
        system.prepare(100f, 80f)
        assertEquals(2, system.particleCount)
        system.paused = true
        system.frameTimeNanos = 10_000_000_000L
        system.prepare(100f, 80f)
        assertEquals(0f, system.snapshot().first()[2])
        system.paused = false
        system.emitting = false
        system.frameTimeNanos = 10_016_666_667L
        system.prepare(100f, 80f)
        assertEquals(2, system.particleCount)
        system.advance(0.2, 100f, 80f)
        assertEquals(0, system.particleCount)
    }

    @Test
    fun `zero sized areas defer initial emission and resizing affects subsequent spawns`() {
        val system = UiParticleSystem(listOf(emitter(60f, 1)), seed = 7)
        system.advance(0.1, 0f, 0f)
        assertEquals(0, system.particleCount)
        system.advance(0.0, 100f, 80f)
        assertEquals(80f, system.snapshot().single()[1])
        system.advance(1.0 / 60.0, 100f, 160f)
        assertEquals(160f, system.snapshot().last()[1])
    }

    @Test
    fun `retirement preserves translucent order and pooled particles reset custom properties`() {
        var spawns = 0
        val emitter = emitter(60f).copy(spawn = {
            x = (++spawns).toFloat()
            if (spawns == 1) {
                lifetime = 0.01f
                velocityY = 900f
                size = 50f
            }
        })
        val system = UiParticleSystem(listOf(emitter), maxParticles = 3)
        repeat(4) { system.advance(1.0 / 60.0, 100f, 80f) }
        assertEquals(listOf(2f, 3f, 4f), system.snapshot().map { it[0] })
        system.forEachParticle {
            assertEquals(0f, it.velocityY)
            assertEquals(8f, it.size)
        }
    }

    @Test
    fun `multiple emitters retain independent rates and custom motion`() {
        val system = UiParticleSystem(listOf(
            emitter(60f).copy(update = { velocityX = 6f }),
            emitter(30f),
        ))
        system.advance(0.1, 100f, 80f)
        val counts = IntArray(2)
        system.forEachParticle {
            counts[it.emitterIndex]++
            if (it.emitterIndex == 0 && it.age > 0f) assertEquals(6f, it.velocityX)
        }
        assertEquals(listOf(6, 3), counts.toList())
    }

    @Test
    fun `invalid settings fail early and invalid spawned particles are discarded`() {
        assertFailsWith<IllegalArgumentException> { emitter(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { UiParticleSystem(emptyList(), maxParticles = 0) }
        assertFailsWith<IllegalArgumentException> { UiParticleSystem(emptyList(), fixedTimeStep = 0.0) }
        val system = UiParticleSystem(listOf(emitter(0f, 3).copy(spawn = { x = Float.NaN })))
        system.advance(0.0, 100f, 80f)
        assertEquals(0, system.particleCount)
        assertFailsWith<IllegalArgumentException> { system.advance(Double.NaN, 100f, 80f) }
        system.reset()
        assertEquals(0, system.particleCount)
    }
}
