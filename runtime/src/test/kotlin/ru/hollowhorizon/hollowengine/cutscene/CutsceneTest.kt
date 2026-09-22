package ru.hollowhorizon.hollowengine.cutscene

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimProperty
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.BlendMode
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ChannelBounds
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ChannelCurve
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.FloatPropertyType
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.KeyInterpolation
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TranslationPropertyType
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CameraRig
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutscenePlaybackController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneWeather
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.TimeOfDayPropertyType
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.TimeOfDayValueFormatter
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.snapTimelineTime
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui.timelineRows
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `group drag preserves spacing when clamped to the work area`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val curve = floatCurve(controller)
        val first = Keyframe(2f, 0f)
        val second = Keyframe(5f, 0f)
        curve.keyframes += listOf(first, second)
        controller.select(listOf(first, second), additive = false)

        controller.beginKeyframeDrag(second)
        controller.applyKeyframeDrag(-10f)
        controller.endKeyframeDrag()

        assertEquals(0f, first.time, 0.001f)
        assertEquals(3f, second.time, 0.001f)
    }

    @Test
    fun `group drag is atomic when one keyframe collides`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val curve = floatCurve(controller)
        val first = Keyframe(1f, 0f)
        val second = Keyframe(3f, 0f)
        curve.keyframes += listOf(first, second, Keyframe(4f, 0f))
        controller.select(listOf(first, second), additive = false)

        controller.beginKeyframeDrag(second)
        controller.applyKeyframeDrag(1f)
        controller.endKeyframeDrag()

        assertEquals(1f, first.time, 0.001f)
        assertEquals(3f, second.time, 0.001f)
    }

    @Test
    fun `channels keep their own keys`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val layer = property.layers.first()
        val x = layer.channels[0]
        val y = layer.channels[1]
        x.keyframes += Keyframe(1f, 5f)
        y.keyframes += Keyframe(1f, 7f)

        controller.select(listOf(x.keyframes.first()), additive = false)
        controller.nudgeSelectedKeyframes(1f)

        assertEquals(2f, x.keyframes.first().time, 0.001f)
        assertEquals(1f, y.keyframes.first().time, 0.001f, "moving X must leave Y where it was")
    }

    @Test
    fun `a hidden layer stops contributing to the value`() {
        val controller = TimelineController()
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV"), 70f),
        )
        property.layers.first().channels.first().keyframes += Keyframe(0f, 90f)
        val shake = property.addLayer("Shake", BlendMode.ADD)
        shake.channels.first().keyframes += Keyframe(0f, 10f)

        assertEquals(100f, property.valueAt(0f), 0.001f)

        shake.isVisible = false
        assertEquals(90f, property.valueAt(0f), 0.001f)
    }

    @Test
    fun `layer weight scales what a layer contributes`() {
        val controller = TimelineController()
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV"), 70f),
        )
        property.layers.first().channels.first().keyframes += Keyframe(0f, 90f)
        val shake = property.addLayer("Shake", BlendMode.ADD)
        shake.channels.first().keyframes += Keyframe(0f, 10f)
        shake.weight = 0.5f

        assertEquals(95f, property.valueAt(0f), 0.001f)
    }

    @Test
    fun `what layers add up to is still held to the bounds`() {
        val controller = TimelineController()
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV", ChannelBounds(maximum = 110f)), 70f),
        )
        controller.setKey(property.layers.first().channels.first(), 0f, 100f)
        val shake = property.addLayer("Shake", BlendMode.ADD)
        controller.setKey(shake.channels.first(), 0f, 40f)

        assertEquals(110f, property.valueAt(0f), 0.001f)
    }

    @Test
    fun `a locked layer refuses new keys`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV"), 70f),
        )
        val layer = property.layers.first()
        layer.isLocked = true

        assertTrue(controller.addKeyframes(layer, 1f).isEmpty())
        assertTrue(layer.channels.first().keyframes.isEmpty())
    }

    @Test
    fun `duplicate times are rejected on one curve`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val curve = floatCurve(controller)
        controller.setKey(curve, 1f, 0f)
        controller.setKey(curve, 1f, 5f)

        assertEquals(1, curve.keyframes.size, "a second key at the same time replaces the first")
        assertEquals(5f, curve.keyframes.first().value, 0.001f)
    }

    @Test
    fun `deleting the selection leaves other curves alone`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val layer = property.layers.first()
        layer.channels.forEach { it.keyframes += Keyframe(1f, 0f) }
        controller.select(listOf(layer.channels[0].keyframes.first()), additive = false)

        controller.deleteSelectedKeyframes()

        assertTrue(layer.channels[0].keyframes.isEmpty())
        assertFalse(layer.channels[1].keyframes.isEmpty())
    }

    @Test
    fun `a type states what its channels may hold`() {
        val controller = TimelineController()
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty(
                "camera.fov",
                "FOV",
                FloatPropertyType("FOV", CameraRig.FOV_BOUNDS),
                CameraRig.DEFAULT_FOV,
            ),
        )
        val key = controller.setKey(property.layers.first().channels.first(), 0f, -40f)

        assertEquals(1f, key.value, 0.001f, "an FOV below one is not a shot")
        assertEquals(1f, property.valueAt(0f), 0.001f)
        assertEquals(ChannelBounds(1f, 200f), property.bounds(0))
    }

    @Test
    fun `copied keys paste onto their own curves at the playhead`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val layer = property.layers.first()
        layer.channels[0].keyframes += Keyframe(1f, 3f)
        layer.channels[1].keyframes += Keyframe(2f, 7f)
        controller.select(layer.channels.flatMap { it.keyframes }, additive = false)

        controller.copySelectedKeyframes()
        controller.pasteKeyframes(5f)

        assertEquals(listOf(1f, 5f), layer.channels[0].keyframes.map { it.time })
        assertEquals(listOf(2f, 6f), layer.channels[1].keyframes.map { it.time }, "spacing survives the paste")
        assertEquals(7f, layer.channels[1].keyframes.last().value, 0.001f)
        assertEquals(2, controller.selectedKeyframes.size, "the copies are what stays selected")
    }

    @Test
    fun `cutting takes the keys with it and pasting brings them back`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val curve = floatCurve(controller)
        curve.keyframes += Keyframe(1f, 4f)
        controller.select(curve.keyframes.toList(), additive = false)

        controller.cutSelectedKeyframes()
        assertTrue(curve.keyframes.isEmpty())

        controller.pasteKeyframes(3f)
        assertEquals(1, curve.keyframes.size)
        assertEquals(3f, curve.keyframes.first().time, 0.001f)
        assertEquals(4f, curve.keyframes.first().value, 0.001f)
    }

    @Test
    fun `a clone drag moves the copies and leaves the originals`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val curve = floatCurve(controller)
        val original = Keyframe(1f, 4f)
        curve.keyframes += original
        controller.select(listOf(original), additive = false)

        assertTrue(controller.beginCloneDrag(original, withValues = false))
        assertTrue(controller.isDragDriver(original), "the pressed key still delivers the drag")
        val clone = controller.dragFocusKeyframe!!
        val start = controller.dragStartTimes!!.getValue(clone)
        controller.applyKeyframeDrag(3f - start)
        controller.endKeyframeDrag()

        assertEquals(2, curve.keyframes.size)
        assertEquals(1f, original.time, 0.001f, "the original stays where it was")
        assertEquals(3f, clone.time, 0.001f)
        assertEquals(4f, clone.value, 0.001f)
    }

    @Test
    fun `focusing a track narrows the graph without hiding it from the preview`() {
        val controller = TimelineController()
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val layer = property.layers.first()
        val x = layer.channels[0]
        x.keyframes += Keyframe(0f, 5f)

        controller.focusCurves(listOf(x), additive = false)
        assertTrue(controller.isFocused(x))
        assertFalse(controller.isFocused(layer.channels[1]))
        assertEquals(5f, property.valueAt(0f).x, 0.001f, "focus is about the graph, not the result")

        controller.focusCurves(listOf(x), additive = false)
        assertTrue(controller.focusedCurves.isEmpty())
    }

    @Test
    fun `clicking a stack of keys steps through it`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val stack = property.layers.first().channels.map { curve ->
            Keyframe(1f, 0f).also { curve.keyframes += it }
        }
        val pressed = stack.first()

        controller.selectStacked(pressed, stack, additive = false)
        assertEquals(listOf(stack[0]), controller.selectedKeyframes.toList())

        controller.selectStacked(pressed, stack, additive = false)
        assertEquals(listOf(stack[1]), controller.selectedKeyframes.toList(), "the second click reaches the key below")

        controller.selectStacked(pressed, stack, additive = false)
        assertEquals(listOf(stack[2]), controller.selectedKeyframes.toList())

        controller.selectStacked(pressed, stack, additive = false)
        assertEquals(listOf(stack[0]), controller.selectedKeyframes.toList(), "and it comes back round")
    }

    @Test
    fun `clicking into a group selection keeps the group`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.translation", "Translation", TranslationPropertyType(), Vec3f.ZERO),
        )
        val stack = property.layers.first().channels.map { curve ->
            Keyframe(1f, 0f).also { curve.keyframes += it }
        }
        controller.select(stack, additive = false)

        controller.selectStacked(stack.first(), stack, additive = false)

        assertEquals(3, controller.selectedKeyframes.size)
    }

    @Test
    fun `undo brings a deleted layer back with its keys`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV"), 70f),
        )
        val base = property.layers.first()
        controller.setKey(base.channels.first(), 0f, 90f)
        val shake = property.addLayer("Shake", BlendMode.ADD)
        controller.setKey(shake.channels.first(), 0f, 10f)

        controller.edit("Delete layer") { property.layers.remove(shake) }
        assertEquals(1, property.layers.size)

        controller.undo()

        assertEquals(2, property.layers.size)
        assertTrue(property.layers[1] === shake, "the layer comes back as itself, not as a copy")
        assertEquals(10f, shake.channels.first().keyframes.first().value, 0.001f)
        assertEquals(100f, property.valueAt(0f), 0.001f)
    }

    @Test
    fun `deleting a middle layer leaves the others with their own keys`() {
        val controller = TimelineController().apply { workAreaEnd = 10f }
        val property = controller.addProperty(
            listOf("Camera"),
            AnimProperty("camera.fov", "FOV", FloatPropertyType("FOV"), 0f),
        )
        val first = property.layers.first()
        controller.setKey(first.channels.first(), 0f, 1f)
        val middle = property.addLayer("Middle", BlendMode.ADD)
        controller.setKey(middle.channels.first(), 0f, 2f)
        val last = property.addLayer("Last", BlendMode.ADD)
        controller.setKey(last.channels.first(), 0f, 4f)

        controller.edit("Delete layer") { property.layers.remove(middle) }

        assertEquals(listOf(1f, 4f), property.layers.map { it.channels.first().keyframes.first().value })
        assertEquals(5f, property.valueAt(0f), 0.001f, "the survivors keep their own keys")
    }

    @Test
    fun `weather keys are discrete even if serialized interpolation says bezier`() {
        val playback = CutscenePlaybackController()
        val curve = playback.weather.layers.first().channels.first()
        val clear = playback.timeline.setKey(curve, 0f, CutsceneWeather.CLEAR.value)
        playback.timeline.setKey(curve, 10f, CutsceneWeather.THUNDER.value)
        clear.interpolation = KeyInterpolation.BEZIER

        assertEquals(CutsceneWeather.CLEAR, playback.weather.valueAt(9.99f))
        assertEquals(CutsceneWeather.THUNDER, playback.weather.valueAt(10f))
    }

    @Test
    fun `environment tracks survive a cutscene round trip`() {
        val playback = CutscenePlaybackController()
        val timeCurve = playback.timeOfDay.layers.first().channels.first()
        val weatherCurve = playback.weather.layers.first().channels.first()
        playback.timeline.setKey(timeCurve, 0f, 6_000f).interpolation = KeyInterpolation.LINEAR
        playback.timeline.setKey(timeCurve, 10f, 18_000f)
        playback.timeline.setKey(weatherCurve, 0f, CutsceneWeather.CLEAR.value)
        playback.timeline.setKey(weatherCurve, 5f, CutsceneWeather.RAIN.value)

        val restored = CutscenePlaybackController()
        restored.setupTracks(playback.toData("Environment"))
        restored.seek(5f)

        assertTrue(restored.timeOfDay.type is TimeOfDayPropertyType)
        assertEquals(12_000f, restored.currentEnvironment.timeOfDay!!, 0.01f)
        assertEquals(CutsceneWeather.RAIN, restored.currentEnvironment.weather)
    }

    @Test
    fun `empty environment tracks do not override the world`() {
        val playback = CutscenePlaybackController()

        assertEquals(null, playback.currentEnvironment.timeOfDay)
        assertEquals(null, playback.currentEnvironment.weather)
    }

    @Test
    fun `time of day formatter follows the minecraft clock`() {
        assertEquals("06:00", TimeOfDayValueFormatter.format(0f))
        assertEquals("12:00", TimeOfDayValueFormatter.format(6_000f))
        assertEquals("00:00", TimeOfDayValueFormatter.format(18_000f))
        assertEquals("05:59", TimeOfDayValueFormatter.format(23_999f))
        assertEquals("07:00", TimeOfDayValueFormatter.format(25_000f))
    }

    @Test
    fun `discrete tracks are omitted from curve editor rows`() {
        val playback = CutscenePlaybackController()
        val rows = timelineRows(playback.timeline, curveEditorOnly = true)

        assertTrue(rows.any { it.property === playback.timeOfDay })
        assertFalse(rows.any { it.property === playback.weather })
    }

    private fun floatCurve(controller: TimelineController): ChannelCurve {
        val property = controller.addProperty(
            listOf("Test"),
            AnimProperty("test.value", "Value", FloatPropertyType(), 0f),
        )
        return property.layers.first().channels.first()
    }
}
