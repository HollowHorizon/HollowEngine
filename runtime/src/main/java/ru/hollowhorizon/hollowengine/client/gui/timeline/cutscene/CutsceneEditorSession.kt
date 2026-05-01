package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.modules.ui2.UiSurface
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.common.utils.rl

class CutsceneEditorSession {
    companion object {
        private val KEY_UNDO = UniversalKeyCode('Z')
        private val KEY_REDO = UniversalKeyCode('Y')
        private val KEY_PLAY_PAUSE = UniversalKeyCode(' ')
    }

    var _propertiesSurface: () -> UiSurface? = { null }
    val propertiesSurface: UiSurface? get() = _propertiesSurface()
    val playback = CutscenePlaybackController()
    val timeline = TimelineController()

    init {
        loadTimelineIcons()
        timeline.workAreaEnd.set(playback.duration)
        timeline.addTrack(listOf("Camera", "Transform"), playback.positionTrack)
        timeline.addTrack(listOf("Camera", "Transform"), playback.rotationTrack)
        timeline.addTrack(listOf("Camera", "Lens"), playback.fovTrack)
        timeline.onChanged = ::syncPlaybackFromTimeline
        timeline.onTimeChanged = ::syncPlaybackFromTimeline
        timeline.onPreviewChanged = ::updatePreviewState
    }

    fun update() {
        timeline.onUpdate()
        syncPlaybackFromTimeline()
    }

    fun captureFrame(time: Float) {
        val pose = CutsceneCameraSystem.capturePlayerPose(Minecraft.getInstance()) ?: return

        timeline.upsertKeyframe(playback.positionTrack, time, pose.position)
        timeline.upsertKeyframe(playback.rotationTrack, time, pose.rotation)
        timeline.upsertKeyframe(playback.fovTrack, time, pose.fov)
        playback.seek(time)
        updatePreviewState()
    }

    fun buildTrackMenu(menu: ItemPopupMenu<AnimTrack<*>>): SubMenuItem<AnimTrack<*>> {
        return SubMenuItem("Track") {
            item("Add keyframe") { track ->
                val time = timeline.trackContextMenuTime ?: timeline.currentTime.value
                timeline.addKeyframe(track, time)
                menu.hide()
            }
            item("Capture frame") { track ->
                captureFrame(timeline.currentTime.value)
            }
            item("Delete selected") {
                timeline.deleteSelectedKeyframes()
                menu.hide()
            }
        }
    }

    fun onKeyInput(event: KeyEvent) {
        if (!event.isPressed) return

        when {
            event.isCtrlDown && event.keyCode == KEY_UNDO -> {
                if (event.isShiftDown) timeline.redo() else timeline.undo()
            }
            event.isCtrlDown && event.keyCode == KEY_REDO -> timeline.redo()
            event.keyCode == KeyboardInput.KEY_DEL -> timeline.deleteSelectedKeyframes()
            event.keyCode == KeyboardInput.KEY_ESC -> timeline.clearSelection()
            event.keyCode == KeyboardInput.KEY_HOME -> timeline.setCurrentTime(0f)
            event.keyCode == KEY_PLAY_PAUSE -> timeline.togglePlayback()
        }
    }

    private fun syncPlaybackFromTimeline() {
        playback.setDuration(timeline.workAreaEnd.value)
        playback.seek(timeline.currentTime.value)
        updatePreviewState()
    }

    private fun updatePreviewState() {
        if (timeline.isCameraPreviewEnabled.value) {
            CutsceneCameraSystem.preview(playback)
        } else if (CutsceneCameraSystem.activeController === playback) {
            CutsceneCameraSystem.stop()
        }
    }

    private fun loadTimelineIcons() {
        timeline.iconPrev = loadIcon("step_backward.svg")
        timeline.iconPlay = loadIcon("play.svg")
        timeline.iconPause = loadIcon("pause.svg")
        timeline.iconNext = loadIcon("step_forward.svg")
        timeline.iconZoomOut = loadIcon("zoom_out.svg")
        timeline.iconZoomIn = loadIcon("zoom_in.svg")
        timeline.iconPulse = loadIcon("pulse.svg")
        timeline.iconFilm = loadIcon("film.svg")
        timeline.iconCompress = loadIcon("compress.svg")
        timeline.visible = loadIcon("visible.svg")
        timeline.invisible = loadIcon("invisible.svg")
        timeline.unlocked = loadIcon("unlocked.svg")
        timeline.locked = loadIcon("locked.svg")
        timeline.arrow = loadIcon("arrow.svg")
    }

    private fun loadIcon(name: String) = ImageManager.load(
        "hollowengine:textures/gui/icons/$name".rl,
        SamplerMode.NEAREST,
    )
}

object CutsceneEditorSessions {
    val default = CutsceneEditorSession()
}
