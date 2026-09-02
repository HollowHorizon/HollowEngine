package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene

import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.*
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

class CutsceneEditorSession {
    val playback = CutscenePlaybackController()
    val timeline = playback.timeline

    val uiRevision = mutableStateOf(0)

    fun invalidateUi() {
        uiRevision.value++
    }

    init {
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
        timeline.captureExtraState = { playback.origin }
        timeline.restoreExtraState = { state -> (state as? CutsceneOrigin)?.let { playback.origin = it } }
    }

    val authoringFrame: CutsceneFrame get() = playback.origin.frame

    fun update(deltaSeconds: Float) {
        timeline.onUpdate(deltaSeconds)
        syncPlaybackFromTimeline()
    }

    fun captureFrame(time: Float) {
        val minecraft = Minecraft.getInstance()
        val pose = CutsceneCameraSystem.capturePlayerPose(minecraft) ?: return
        val environment = minecraft.level?.captureCutsceneEnvironment() ?: return
        val frame = authoringFrame
        timeline.edit("Capture keyframe") {
            timeline.clearSelection()
            val position = frame.toLocal(pose.position)
            writeChannels(playback.translation, time, listOf(position.x, position.y, position.z))
            writeChannels(playback.rotation, time, frame.toLocalRotation(pose.rotation).decomposedBy(playback.rotation))
            writeChannels(playback.fov, time, listOf(pose.fov))
            writeChannels(playback.timeOfDay, time, listOfNotNull(environment.timeOfDay))
            writeChannels(playback.weather, time, listOfNotNull(environment.weather?.value))
        }
        playback.seek(time)
        updatePreviewState()
    }

    private fun <T> T.decomposedBy(property: AnimProperty<T>): List<Float> {
        val values = FloatArray(property.channels.size)
        property.type.decompose(this, values)
        return values.toList()
    }

    private fun writeChannels(property: AnimProperty<*>, time: Float, values: List<Float>) {
        val layer = layerFor(property) ?: return
        if (timeline.isLocked(layer)) return
        val created = layer.channels.mapIndexedNotNull { channel, curve ->
            val value = values.getOrNull(channel) ?: return@mapIndexedNotNull null
            val unwrapped = curve.spec.unwrap(value, curve.valueAt(time, value))
            timeline.setKey(curve, time, unwrapped, selectKey = false)
        }
        timeline.select(created, additive = true)
    }

    fun layerFor(property: AnimProperty<*>): AnimLayer? = timeline.targetLayer(property)

    fun moveOrigin(position: Vec3f, yaw: Float, keepWorld: Boolean) {
        timeline.edit(if (keepWorld) "Re-anchor cutscene" else "Move cutscene") {
            if (keepWorld) playback.reanchor(position, yaw)
            else playback.origin = playback.origin.moved(position, yaw)
        }
        syncPlaybackFromTimeline()
        invalidateUi()
    }

    /** Puts the origin on the player standing in the world, the usual way to place a scene. */
    fun originToPlayer(keepWorld: Boolean) {
        val player = Minecraft.getInstance().player ?: return
        moveOrigin(Vec3f(player.x.toFloat(), player.y.toFloat(), player.z.toFloat()), player.yRot, keepWorld)
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

            ctrl && key == GLFW.GLFW_KEY_TAB -> {
                if (timeline.viewMode == TimelineViewMode.CURVES) timeline.viewMode = TimelineViewMode.DOPE_SHEET
                else timeline.enterCurveView()
                true
            }

            key == GLFW.GLFW_KEY_F -> {
                timeline.frameCurves()
                true
            }

            key == GLFW.GLFW_KEY_S -> {
                timeline.smoothSelectedKeyframes()
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

            ctrl && key == GLFW.GLFW_KEY_C -> {
                timeline.copySelectedKeyframes()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_X -> {
                timeline.cutSelectedKeyframes()
                true
            }

            ctrl && key == GLFW.GLFW_KEY_V -> {
                timeline.pasteKeyframes()
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
                timeline.applyCurrentTime(0f)
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
            timeline.applyCurrentTime(timeline.currentTime + deltaSeconds)
        } else {
            timeline.nudgeSelectedKeyframes(deltaSeconds)
        }
    }

    fun syncPlaybackFromTimeline() {
        playback.updateProperties()
        updatePreviewState()
    }

    fun updatePreviewState() {
        if (timeline.isCameraPreviewEnabled) {
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
        timeline.isPlaying = false
        timeline.applyCurrentTime(0f)
        timeline.clearSelection()
        timeline.clearHistory()
        updatePreviewState()
    }

    fun channelValues(property: AnimProperty<*>): List<Float> = property.decomposeAt(timeline.currentTime).toList()
}

private const val KEYFRAME_NUDGE_SMALL = 0.05f
private const val KEYFRAME_NUDGE_LARGE = 0.25f

object CutsceneEditorSessions {
    val default = CutsceneEditorSession()
}
