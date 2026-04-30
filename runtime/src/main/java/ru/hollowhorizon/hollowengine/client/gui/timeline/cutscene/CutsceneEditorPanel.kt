package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.*
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.PropertiesPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TimelineArea
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.Toolbar
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TrackHeaderList
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.generated.Assets

class CutsceneEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.cutscene", dock) {
    companion object {
        private val KEY_UNDO = UniversalKeyCode('Z')
        private val KEY_REDO = UniversalKeyCode('Y')
        private val KEY_PLAY_PAUSE = UniversalKeyCode(' ')
    }

    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.FILM

    private val playback = CutscenePlaybackController()
    private val timeline = TimelineController()
    private val status = mutableStateOf("Camera cutscene")

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

    override fun UiScope.drawHeaderLeft() {
        Toolbar(timeline) {
            Button("Capture") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.smallGap)
                    .onClick { captureCurrentCamera() }
            }

            Button("Stop") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.smallGap)
                    .onClick {
                        timeline.isPlaying.set(false)
                        CutsceneCameraSystem.stop()
                        status.set("Camera preview stopped")
                    }
            }
        }
    }

    override fun UiScope.compose() {
        val trackMenu = remember { ItemPopupMenu<AnimTrack<*>>("cutscene-track-menu") }
        timeline.onTrackContextMenu = { event, track ->
            trackMenu.show(event.screenPosition, buildTrackMenu(trackMenu), track)
        }

        Column(Grow.Std, Grow.Std) {


            Row(Grow.Std, Grow.Std) {
                TrackHeaderList(timeline)
                Splitter { delta ->
                    timeline.trackPanelWidth.set((timeline.trackPanelWidth.value + Dp.fromPx(delta).value).coerceIn(180f, 520f))
                }
                TimelineArea(timeline)
                Splitter { delta ->
                    timeline.propertiesPanelWidth.set((timeline.propertiesPanelWidth.value - Dp.fromPx(delta).value).coerceIn(180f, 460f))
                }
                PropertiesPanel(timeline)
            }

            Box(Grow.Std, 22.dp) {
                Text(status.use()) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .margin(start = sizes.gap)
                        .textColor(Color.WHITE.withAlpha(0.65f))
                }
            }
        }

        trackMenu()
        timeline.onUpdate()
        syncPlaybackFromTimeline()
    }

    private fun captureCurrentCamera() {
        val pose = CutsceneCameraSystem.capturePlayerPose(Minecraft.getInstance()) ?: return
        val time = timeline.currentTime.value

        timeline.upsertKeyframe(playback.positionTrack, time, pose.position)
        timeline.upsertKeyframe(playback.rotationTrack, time, pose.rotation)
        timeline.upsertKeyframe(playback.fovTrack, time, pose.fov)
        playback.seek(time)
        status.set("Captured camera at ${"%.2f".format(time)}s")
        updatePreviewState()
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

    private fun buildTrackMenu(menu: ItemPopupMenu<AnimTrack<*>>): SubMenuItem<AnimTrack<*>> {
        return SubMenuItem("Track") {
            item("Add keyframe") { track ->
                val time = timeline.trackContextMenuTime ?: timeline.currentTime.value
                timeline.addKeyframe(track, time)
                menu.hide()
            }
            item("Delete selected") {
                timeline.deleteSelectedKeyframes()
                menu.hide()
            }
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

    override fun onKeyInput(event: KeyEvent) {
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

    private fun UiScope.Splitter(onMove: (Float) -> Unit) {
        Box(6.dp, Grow.Std) {
            modifier
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .onHover { PointerInput.cursorShape = CursorShape.RESIZE_E }
                .onDrag { event ->
                    onMove(event.pointer.delta.x)
                    event.pointer.consume()
                }
        }
    }
}
