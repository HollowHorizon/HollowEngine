package ru.hollowhorizon.hollowengine.cutscene

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.PropertyDriver
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Vec3PropertyDriver
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CameraCutsceneTracks
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutscenePlaybackController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneTrackRegistry
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.EasingRegistry
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.snapTimelineTime
import ru.hollowhorizon.hollowengine.common.utils.math.Easing
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CutsceneTest {

    @Test
    fun `timeline snapping follows drag modifiers`() {
        assertEquals(1f, snapTimelineTime(1.24f, GLFW.GLFW_MOD_ALT))
        assertEquals(2f, snapTimelineTime(1.76f, GLFW.GLFW_MOD_ALT))
        assertEquals(1f, snapTimelineTime(1.24f, GLFW.GLFW_MOD_SHIFT))
        assertEquals(1.5f, snapTimelineTime(1.26f, GLFW.GLFW_MOD_SHIFT))
        assertEquals(1.24f, snapTimelineTime(1.24f, GLFW.GLFW_MOD_CONTROL))
        assertEquals(1.24f, snapTimelineTime(1.24f, 0))
    }

    @Test
    fun `easing registry resolves all names`() {
        val allNames = listOf(
            "linear", "smooth",
            "easeInSine", "easeOutSine", "easeInOutSine",
            "easeInQuad", "easeOutQuad", "easeInOutQuad",
            "easeInCubic", "easeOutCubic", "easeInOutCubic",
            "easeInQuart", "easeOutQuart", "easeInOutQuart",
            "easeInQuint", "easeOutQuint", "easeInOutQuint",
            "easeInExpo", "easeOutExpo", "easeInOutExpo",
            "easeInCirc", "easeOutCirc", "easeInOutCirc",
            "easeInBack", "easeOutBack", "easeInOutBack",
            "easeInBounce", "easeOutBounce", "easeInOutBounce",
            "easeInElastic", "easeOutElastic", "easeInOutElastic"
        )

        for (name in allNames) {
            val easing = EasingRegistry.resolve(name)
            assertNotNull(easing, "Easing not found for: $name")
        }
    }

    @Test
    fun `unknown easing falls back to linear`() {
        val easing = EasingRegistry.resolve("nonexistent")
        assertEquals(Easing.linear, easing)
    }

    @Test
    fun `easing produces valid values in 0 to 1 range`() {
        val testEasings = listOf(
            "linear" to Easing.linear,
            "easeInOutQuad" to Easing.easeInOutQuad,
            "easeOutElastic" to Easing.easeOutElastic,
            "easeInBounce" to Easing.easeInBounce
        )

        for ((name, easing) in testEasings) {
            assertEquals(0f, easing.eased(0f), 0.01f, "$name at t=0 should be ~0")
            assertEquals(1f, easing.eased(1f), 0.01f, "$name at t=1 should be ~1")
            val mid = easing.eased(0.5f)
            assertTrue(mid >= -0.25f && mid <= 1.25f, "$name at t=0.5 should stay near [0,1], got $mid")
        }
    }

    @Test
    fun `vec3 property driver interpolation`() {
        val driver = Vec3PropertyDriver {}

        val start = Vec3f(0f, 0f, 0f)
        val end = Vec3f(10f, 20f, 30f)

        val result0 = driver.interpolate(start, end, 0f)
        assertEquals(start.x, result0.x, 0.001f)
        assertEquals(start.y, result0.y, 0.001f)
        assertEquals(start.z, result0.z, 0.001f)

        val result1 = driver.interpolate(start, end, 1f)
        assertEquals(end.x, result1.x, 0.001f)
        assertEquals(end.y, result1.y, 0.001f)
        assertEquals(end.z, result1.z, 0.001f)

        val resultMid = driver.interpolate(start, end, 0.5f)
        assertEquals(5f, resultMid.x, 0.001f)
        assertEquals(10f, resultMid.y, 0.001f)
        assertEquals(15f, resultMid.z, 0.001f)
    }

    @Test
    fun `animtrack interpolation with easing`() {
        var lastValue = 0f
        val driver = object : PropertyDriver<Float> {
            override fun interpolate(start: Float, end: Float, fraction: Float): Float =
                start + (end - start) * fraction

            override fun apply(value: Float) {
                lastValue = value
            }

        }

        val track = AnimTrack(
            name = "Test",
            driver = driver,
            defaultValue = 0f,
            keyframes = mutableListOf(
                Keyframe(0f, 0f, Easing.linear),
                Keyframe(2f, 100f, Easing.linear)
            )
        )

        track.update(0f)
        assertEquals(0f, lastValue, 0.1f)

        track.update(1f)
        assertEquals(50f, lastValue, 1f)

        track.update(2f)
        assertEquals(100f, lastValue, 1f)
    }

    @Test
    fun `group drag preserves spacing when clamped to the work area`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val first = Keyframe(2f, 0f)
        val second = Keyframe(5f, 0f)
        val track = floatTrack(first, second)
        controller.addTrack("Test", track)
        controller.selectedKeyframes.addAll(listOf(first, second))

        controller.beginKeyframeDrag(second)
        controller.applyKeyframeDrag(-10f)
        controller.endKeyframeDrag()

        assertEquals(0f, first.time, 0.001f)
        assertEquals(3f, second.time, 0.001f)
    }

    @Test
    fun `group drag is atomic when one keyframe collides`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val first = Keyframe(1f, 0f)
        val second = Keyframe(3f, 0f)
        val blocker = Keyframe(4f, 0f)
        val track = floatTrack(first, second, blocker)
        controller.addTrack("Test", track)
        controller.selectedKeyframes.addAll(listOf(first, second))

        controller.beginKeyframeDrag(second)
        controller.applyKeyframeDrag(1f)
        controller.endKeyframeDrag()

        assertEquals(1f, first.time, 0.001f)
        assertEquals(3f, second.time, 0.001f)
    }

    private fun floatTrack(vararg keyframes: Keyframe<Float>): AnimTrack<Float> {
        val driver = object : PropertyDriver<Float> {
            override fun interpolate(start: Float, end: Float, fraction: Float): Float =
                start + (end - start) * fraction

            override fun apply(value: Float) = Unit

        }
        return AnimTrack("Test", driver, 0f, keyframes.toMutableList())
    }

    @Test
    fun `playback controller reads generic camera tracks`() {
        val source = CutscenePlaybackController()
        source.positionTrack.keyframes.add(Keyframe(0f, Vec3f(1f, 2f, 3f)))
        source.rotationTrack.keyframes.add(Keyframe(0f, Vec3f(10f, 20f, 30f)))
        source.fovTrack.keyframes.add(Keyframe(0f, 55f))

        val data = source.toData()
        val controller = CutscenePlaybackController()
        controller.setupTracks(data)

        assertEquals(1, controller.positionTrack.keyframes.size)
        assertEquals(1, controller.rotationTrack.keyframes.size)
        assertEquals(1, controller.fovTrack.keyframes.size)
        assertEquals(55f, controller.fovTrack.keyframes.first().value)
        assertNotNull(CutsceneTrackRegistry.get(CameraCutsceneTracks.POSITION_ID))
    }

    @Test
    fun `timeline controller rejects duplicate keyframe times`() {
        val controller = TimelineController()
        val track = AnimTrack("Test", Vec3PropertyDriver {}, Vec3f.ZERO)
        controller.addTrack("Camera", track)

        val first = controller.addKeyframe(track, 1f, Vec3f(1f, 0f, 0f))
        val duplicate = controller.addKeyframe(track, 1f, Vec3f(2f, 0f, 0f))

        assertNotNull(first)
        assertEquals(null, duplicate)
        assertEquals(1, track.keyframes.size)
    }

    @Test
    fun `playback controller state machine`() {
        val controller = CutscenePlaybackController()

        assertEquals(false, controller.isPlaying)

        controller.play()
        assertEquals(true, controller.isPlaying)

        controller.pause()
        assertEquals(false, controller.isPlaying)

        controller.play()
        assertEquals(true, controller.isPlaying)

        controller.stop()
        assertEquals(false, controller.isPlaying)
        assertEquals(0f, controller.currentTime)
    }
}
