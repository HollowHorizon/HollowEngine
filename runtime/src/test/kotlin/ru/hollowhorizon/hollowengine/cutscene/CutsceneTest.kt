package ru.hollowhorizon.hollowengine.cutscene

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.UiScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.PropertyDriver
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.Vec3PropertyDriver
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CameraCutsceneTracks
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneData
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneKeyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutscenePlaybackController
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneTrackRegistry
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.EasingRegistry
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.FloatSerializable
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.Vec3Serializable
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.toSerializable
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.toVec3f
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CutsceneTest {

    private val testJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `serialize and deserialize cutscene data`() {
        val original = CutsceneData(
            name = "Test Cutscene",
            duration = 15f,
            positionKeyframes = listOf(
                CutsceneKeyframe(0f, Vec3Serializable(0f, 0f, 0f), "linear"),
                CutsceneKeyframe(5f, Vec3Serializable(10f, 5f, 0f), "easeInOutQuad"),
                CutsceneKeyframe(10f, Vec3Serializable(20f, 0f, 5f), "easeOutCubic")
            ),
            rotationKeyframes = listOf(
                CutsceneKeyframe(0f, Vec3Serializable(0f, 0f, 0f), "linear"),
                CutsceneKeyframe(7f, Vec3Serializable(0f, 180f, 0f), "easeInOutSine")
            ),
            fovKeyframes = listOf(
                CutsceneKeyframe(0f, FloatSerializable(70f), "linear"),
                CutsceneKeyframe(5f, FloatSerializable(30f), "easeInOutExpo")
            )
        )

        val json = testJson.encodeToString(CutsceneData.serializer(), original)
        val deserialized = testJson.decodeFromString(CutsceneData.serializer(), json)

        assertEquals(original.name, deserialized.name)
        assertEquals(original.duration, deserialized.duration)
        assertEquals(original.positionKeyframes.size, deserialized.positionKeyframes.size)
        assertEquals(original.rotationKeyframes.size, deserialized.rotationKeyframes.size)
        assertEquals(original.fovKeyframes.size, deserialized.fovKeyframes.size)

        val firstPos = deserialized.positionKeyframes[0]
        assertEquals(0f, firstPos.time)
        assertEquals(0f, firstPos.value.x)
        assertEquals("linear", firstPos.easing)

        val midPos = deserialized.positionKeyframes[1]
        assertEquals(5f, midPos.time)
        assertEquals(10f, midPos.value.x)
        assertEquals(5f, midPos.value.y)
        assertEquals("easeInOutQuad", midPos.easing)
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
    fun `vec3 serializable roundtrip`() {
        val original = Vec3f(3.5f, -2.1f, 7.8f)
        val serializable = original.toSerializable()
        val back = serializable.toVec3f()

        assertEquals(original.x, back.x, 0.001f)
        assertEquals(original.y, back.y, 0.001f)
        assertEquals(original.z, back.z, 0.001f)
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

            override fun UiScope.drawEditor(value: Float, onChange: (Float) -> Unit) {
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
    fun `playback controller setup and data extraction`() {
        val data = CutsceneData(
            duration = 8f,
            positionKeyframes = listOf(
                CutsceneKeyframe(0f, Vec3Serializable(0f, 0f, 0f)),
                CutsceneKeyframe(4f, Vec3Serializable(10f, 0f, 0f), "easeInOutQuad")
            ),
            rotationKeyframes = listOf(
                CutsceneKeyframe(0f, Vec3Serializable(0f, 0f, 0f)),
                CutsceneKeyframe(4f, Vec3Serializable(0f, 90f, 0f))
            ),
            fovKeyframes = listOf(
                CutsceneKeyframe(0f, FloatSerializable(70f)),
                CutsceneKeyframe(4f, FloatSerializable(30f))
            )
        )

        val controller = CutscenePlaybackController()
        controller.setupTracks(data)

        assertEquals(8f, controller.duration)

        val extracted = controller.toData()
        assertEquals(2, extracted.positionKeyframes.size)
        assertEquals(2, extracted.rotationKeyframes.size)
        assertEquals(2, extracted.fovKeyframes.size)
        assertEquals(1, extracted.nodes.size)
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

    @Test
    fun `playback controller seek`() {
        val data = CutsceneData(
            duration = 10f,
            positionKeyframes = listOf(
                CutsceneKeyframe(0f, Vec3Serializable(0f, 0f, 0f)),
                CutsceneKeyframe(5f, Vec3Serializable(10f, 0f, 0f))
            )
        )

        val controller = CutscenePlaybackController()
        controller.setupTracks(data)

        controller.seek(3f)
        assertEquals(3f, controller.currentTime, 0.01f)

        controller.seek(15f)
        assertEquals(10f, controller.currentTime, 0.01f)

        controller.seek(-5f)
        assertEquals(0f, controller.currentTime, 0.01f)
    }

    @Test
    fun `cutscene data default values`() {
        val data = CutsceneData()
        assertEquals("New Cutscene", data.name)
        assertEquals(10f, data.duration)
        assertTrue(data.positionKeyframes.isEmpty())
        assertTrue(data.rotationKeyframes.isEmpty())
        assertTrue(data.fovKeyframes.isEmpty())
    }
}
