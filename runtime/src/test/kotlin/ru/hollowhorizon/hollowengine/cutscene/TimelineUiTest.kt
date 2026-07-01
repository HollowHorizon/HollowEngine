package ru.hollowhorizon.hollowengine.cutscene

import de.fabmax.kool.util.Color
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.FloatPropertyDriver
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.HollowTimelineEditor
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.timelineTimeAt
import ru.hollowhorizon.hollowengine.client.ui.DrawShapeCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUiInputController
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.shape.UiShapeSize
import ru.hollowhorizon.hollowengine.client.ui.dispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimelineUiTest {
    @Test
    fun `timeline keyframes render through shape commands`() {
        val controller = timelineController(trackCount = 1)

        HollowUiSurface().use { surface ->
            val frame = surface.frame(
                content = { HollowTimelineEditor(controller) },
                width = 820f,
                height = 260f,
            )
            val keyShapeCommands = frame.commands
                .filterIsInstance<DrawShapeCommand>()
                .filter { it.node.id?.startsWith("keyframe-Value 0-") == true }

            assertEquals(2, keyShapeCommands.size)
            assertTrue(keyShapeCommands.all { it.shape.createPath(UiShapeSize(20f, 20f)).commands.isNotEmpty() })
        }
    }

    @Test
    fun `timeline scrollable area exposes horizontal and vertical range`() {
        val controller = timelineController(trackCount = 12)

        HollowUiSurface().use { surface ->
            surface.setContent { HollowTimelineEditor(controller) }
            val frame = surface.frame(820f, 180f)
            val timelineScroll = frame.resolved.styles.keys.single { it.id == "timeline-scroll" }
            val range = frame.layout[timelineScroll].scrollRange

            assertTrue(range.x > 0f)
            assertTrue(range.y > 0f)

            surface.scroll(timelineScroll, deltaX = 80f, deltaY = 48f)
            val scrolled = surface.frame(820f, 180f)

            assertEquals(80f, scrolled.layout[timelineScroll].scrollOffset.x)
            assertEquals(48f, scrolled.layout[timelineScroll].scrollOffset.y)
        }
    }

    @Test
    fun `dragging keyframe moves it on the timeline`() {
        val controller = timelineController(trackCount = 1)
        val track = controller.getAllTracks().filterIsInstance<AnimTrack<Float>>().single()
        val keyframe = track.keyframes.first()
        val input = HollowUiInputController()

        HollowUiSurface().use { surface ->
            surface.setContent { HollowTimelineEditor(controller) }
            val frame = surface.frame(820f, 260f)
            val keyNode = frame.resolved.styles.keys.single { it.id?.startsWith("keyframe-Value 0-1.0") == true }
            val rect = frame.layout[keyNode].rect
            val x = rect.x + rect.width * 0.5f
            val y = rect.y + rect.height * 0.5f

            input.mouseClicked(frame, x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, ::dispatchEvent, {}, modifiers = 0)
            input.mouseDragged(frame, x + 50f, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, deltaX = 50f, deltaY = 0f, ::dispatchEvent)
            input.mouseReleased(frame, x + 50f, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, ::dispatchEvent)

            assertSame(keyframe, controller.selectedKeyframes.single())
            assertEquals(1.5f, keyframe.time)
        }
    }

    @Test
    fun `right pressing track lane inserts keyframe at pointer time`() {
        val controller = timelineController(trackCount = 1)
        val track = controller.getAllTracks().filterIsInstance<AnimTrack<Float>>().single()
        val input = HollowUiInputController()

        HollowUiSurface().use { surface ->
            surface.setContent { HollowTimelineEditor(controller) }
            val frame = surface.frame(820f, 260f)
            val lane = frame.resolved.styles.keys.single { it.id == "lane-timeline-track-1" }
            val rect = frame.layout[lane].rect
            val x = rect.x + 360f
            val y = rect.y + rect.height * 0.5f
            val expectedTime = timelineTimeAt(360f, controller.pixelsPerSecond.value, controller.workAreaEnd.value)

            input.mouseClicked(frame, x, y, GLFW.GLFW_MOUSE_BUTTON_RIGHT, ::dispatchEvent, {}, modifiers = 0)

            assertTrue(track.keyframes.any { it.time == expectedTime })
        }
    }

    private fun timelineController(trackCount: Int): TimelineController {
        val controller = TimelineController()
        controller.workAreaEnd.set(8f)
        repeat(trackCount) { index ->
            val track = AnimTrack(
                name = "Value $index",
                driver = FloatPropertyDriver("Value") {},
                defaultValue = 0f,
                keyframes = mutableListOf(
                    Keyframe(1f, 0f),
                    Keyframe(2f, 0f),
                ),
                trackColor = Color.ORANGE,
            )
            controller.addTrack(listOf("Camera"), track)
        }
        return controller
    }

    private fun dispatchEvent(event: UiEvent): Boolean {
        return event.node.dispatch(event)
    }
}
