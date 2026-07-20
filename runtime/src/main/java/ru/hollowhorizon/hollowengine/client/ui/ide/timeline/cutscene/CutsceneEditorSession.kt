package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene

import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController

class CutsceneEditorSession {
    val playback = CutscenePlaybackController()
    val timeline = TimelineController()

    val uiRevision = mutableStateOf(0)

    fun invalidateUi() {
        uiRevision.value++
    }

    init {
        timeline.workAreaEnd.set(playback.duration)
        timeline.addTrack(listOf("Camera", "Transform"), playback.positionTrack)
        timeline.addTrack(listOf("Camera", "Transform"), playback.rotationTrack)
        timeline.addTrack(listOf("Camera", "Lens"), playback.fovTrack)
        timeline.onChanged = {
            syncPlaybackFromTimeline()
            invalidateUi()
        }
        timeline.onTimeChanged = {
            syncPlaybackFromTimeline()
            invalidateUi()
        }
        timeline.onPreviewChanged = {
            updatePreviewState()
            invalidateUi()
        }
    }

    fun update(deltaSeconds: Float) {
        timeline.onUpdate(deltaSeconds)
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

    fun onHollowUiKey(key: Int, modifiers: Int): Boolean {
        val ctrl = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        val shift = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val handled = when {
            ctrl && key == GLFW.GLFW_KEY_Z -> {
                if (shift) timeline.redo() else timeline.undo()
                true
            }
            ctrl && key == GLFW.GLFW_KEY_Y -> {
                timeline.redo()
                true
            }
            key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE -> {
                timeline.deleteSelectedKeyframes()
                true
            }
            ctrl && key == GLFW.GLFW_KEY_D -> {
                timeline.duplicateSelectedKeyframes()
                true
            }
            key == GLFW.GLFW_KEY_LEFT -> {
                moveSelectionOrPlayhead(if (shift) -KEYFRAME_NUDGE_LARGE else -KEYFRAME_NUDGE_SMALL)
                true
            }
            key == GLFW.GLFW_KEY_RIGHT -> {
                moveSelectionOrPlayhead(if (shift) KEYFRAME_NUDGE_LARGE else KEYFRAME_NUDGE_SMALL)
                true
            }
            key == GLFW.GLFW_KEY_ESCAPE -> {
                timeline.clearSelection()
                true
            }
            key == GLFW.GLFW_KEY_HOME -> {
                timeline.setCurrentTime(0f)
                true
            }
            key == GLFW.GLFW_KEY_SPACE -> {
                timeline.togglePlayback()
                true
            }
            else -> false
        }
        if (handled) invalidateUi()
        return handled
    }

    private fun moveSelectionOrPlayhead(deltaSeconds: Float) {
        if (timeline.selectedKeyframes.isEmpty()) {
            timeline.setCurrentTime(timeline.currentTime.value + deltaSeconds)
        } else {
            timeline.nudgeSelectedKeyframes(deltaSeconds)
        }
    }

    fun syncPlaybackFromTimeline() {
        playback.setDuration(timeline.workAreaEnd.value)
        playback.seek(timeline.currentTime.value)
        updatePreviewState()
    }

    fun updatePreviewState() {
        if (timeline.isCameraPreviewEnabled.value) {
            CutsceneCameraSystem.preview(playback)
        } else if (CutsceneCameraSystem.activeController === playback) {
            CutsceneCameraSystem.stop()
        }
    }

    fun exportCutscene(path: String, name: String) {
        CutsceneStorage.save(path, name, playback.toData(name))
    }

    fun importCutscene(readablePath: String) {
        val data = CutsceneStorage.load(readablePath)
        playback.setupTracks(data)
        timeline.isPlaying.set(false)
        timeline.workAreaEnd.set(playback.duration)
        timeline.setCurrentTime(0f)
        timeline.clearSelection()
        timeline.clearHistory()
        updatePreviewState()
    }
}

private const val KEYFRAME_NUDGE_SMALL = 0.05f
private const val KEYFRAME_NUDGE_LARGE = 0.25f

object CutsceneEditorSessions {
    val default = CutsceneEditorSession()
}
