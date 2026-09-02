package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ChannelCurve
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.CurvePresets
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.HandleMode
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.KeyInterpolation
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.KeyTangent
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TangentSide
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene.CutsceneEditorSession
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

@Composable
internal fun HollowTimelineProperties(
    session: CutsceneEditorSession,
    modifier: Modifier,
    refresh: () -> Unit,
) {
    val controller = session.timeline
    val selectedKey = controller.selectedKeyframes.firstOrNull()
    val selectedCurve = selectedKey?.let { controller.curveOf(it) }

    Column(
        id = "timeline-properties",
        modifier = modifier.background(TimelineColors.Panel)
            .border(1.px, TimelineColors.Border)
            .padding(10.px)
            .gap(8.px)
            .scrollable(horizontal = false)
    ) {
        Text(CutsceneLang.PROPERTIES.lang, modifier = Modifier.fontSize(13f).foreground(TimelineColors.Text))
        PreviewSection(controller, refresh)
        OriginSection(session, refresh)
        when {
            selectedKey != null && selectedCurve != null -> {
                KeyframeSection(session, selectedKey, selectedCurve, refresh)
                CurveSection(controller, selectedKey, selectedCurve, refresh)
                ToolbarButton(
                    if (controller.selectedKeyframes.size == 1) CutsceneLang.DELETE_KEY.lang
                    else CutsceneLang.DELETE_KEYS.lang,
                    "timeline-properties-delete",
                    TimelineColors.Danger,
                ) {
                    controller.deleteSelectedKeyframes()
                    refresh()
                }
            }

            controller.isWorkAreaSelected -> WorkAreaSection(controller, refresh)
            else -> EmptySection()
        }
    }
}

@Composable
private fun PreviewSection(controller: TimelineController, refresh: () -> Unit) {
    Section(CutsceneLang.PREVIEW.lang) {
        Row(
            modifier = Modifier.size(100.percent, 24.px)
                .alignItems(vertical = UiAlign.CENTER)
                .gap(8.px)
        ) {
            TogglePill(
                if (controller.isCameraPreviewEnabled) CutsceneLang.CAMERA_ON.lang
                else CutsceneLang.CAMERA_OFF.lang,
                controller.isCameraPreviewEnabled
            ) {
                controller.applyCameraPreviewEnabled(!controller.isCameraPreviewEnabled)
                refresh()
            }
            Text(
                if (controller.isPlaying) CutsceneLang.PLAYING.lang else CutsceneLang.PAUSED.lang,
                modifier = Modifier.fontSize(10f).foreground(TimelineColors.Muted),
            )
        }
        PropertyLine(CutsceneLang.CURRENT_TIME.lang, "%.3f s".format(controller.currentTime).replace(',', '.'))
        PropertyLine(CutsceneLang.DURATION.lang, "%.3f s".format(controller.workAreaEnd).replace(',', '.'))
    }
}

@Composable
private fun OriginSection(session: CutsceneEditorSession, refresh: () -> Unit) {
    val origin = session.playback.origin
    Section(CutsceneLang.ORIGIN.lang, id = "timeline-origin-section") {
        FloatField(CutsceneLang.ORIGIN_X.lang, origin.x, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
            session.moveOrigin(Vec3f(next, origin.y, origin.z), origin.yaw, keepWorld = false)
            refresh()
        }
        FloatField(CutsceneLang.ORIGIN_Y.lang, origin.y, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
            session.moveOrigin(Vec3f(origin.x, next, origin.z), origin.yaw, keepWorld = false)
            refresh()
        }
        FloatField(CutsceneLang.ORIGIN_Z.lang, origin.z, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
            session.moveOrigin(Vec3f(origin.x, origin.y, next), origin.yaw, keepWorld = false)
            refresh()
        }
        FloatField(CutsceneLang.ORIGIN_YAW.lang, origin.yaw, -360f, 360f) { next ->
            session.moveOrigin(origin.position, next, keepWorld = false)
            refresh()
        }
        Row(modifier = Modifier.size(100.percent, UiLength.Auto).gap(6.px)) {
            ToolbarButton(CutsceneLang.ORIGIN_MOVE_HERE.lang, "timeline-origin-move") {
                session.originToPlayer(keepWorld = false)
                refresh()
            }
            ToolbarButton(CutsceneLang.ORIGIN_REBASE.lang, "timeline-origin-rebase") {
                session.originToPlayer(keepWorld = true)
                refresh()
            }
        }
    }
}

@Composable
private fun KeyframeSection(
    session: CutsceneEditorSession,
    keyframe: Keyframe,
    curve: ChannelCurve,
    refresh: () -> Unit,
) {
    val controller = session.timeline
    val count = controller.selectedKeyframes.size
    val title = if (count == 1) CutsceneLang.KEYFRAME.lang else CutsceneLang.KEYFRAMES.lang(count)
    Section(title) {
        PropertyLine(CutsceneLang.CHANNEL.lang, channelPath(controller, curve))
        FloatField(CutsceneLang.TIME.lang, keyframe.time, 0f, controller.workAreaEnd) { time ->
            controller.nudgeSelectedKeyframes(snapTimelineTime(time, currentUiKeyModifiers()) - keyframe.time)
            refresh()
        }
        FloatField(CutsceneLang.VALUE.lang, keyframe.value, -Float.MAX_VALUE, Float.MAX_VALUE) { next ->
            val delta = next - keyframe.value
            controller.edit("Edit keyframe value") {
                controller.selectedKeyframes.forEach { it.value += delta }
            }
            refresh()
        }
        WorldReadout(session)
    }
}

private fun channelPath(controller: TimelineController, curve: ChannelCurve): String {
    val layer = controller.allLayers().firstOrNull { it.channels.any { channel -> channel === curve } }
    val property = layer?.let { controller.propertyOf(it) }
    return listOfNotNull(property?.nameState, layer?.nameState, curve.name).joinToString(" / ")
}

@Composable
private fun WorldReadout(session: CutsceneEditorSession) {
    val pose = session.playback.currentPose
    PropertyLine(CutsceneLang.WORLD.lang, formatVec3(pose.position))
    PropertyLine(CutsceneLang.WORLD_ROTATION.lang, formatVec3(pose.rotation))
}

private fun formatVec3(vector: Vec3f): String =
    "%.2f  %.2f  %.2f".format(vector.x, vector.y, vector.z).replace(',', '.')

/**
 * Interpolation is a curve preset: picking one writes the handles that shape the segment, and the
 * handles can then be dragged from here or in the graph editor.
 */
@Composable
private fun CurveSection(
    controller: TimelineController,
    keyframe: Keyframe,
    curve: ChannelCurve,
    refresh: () -> Unit,
) {
    val index = curve.keyframes.indexOfFirst { it === keyframe }
    val next = curve.keyframes.getOrNull(index + 1)
    val active = CurvePresets.match(keyframe, next)
    var category by remember { mutableStateOf(active?.category ?: CurvePresets.categories.first()) }

    Section(CutsceneLang.INTERPOLATION.lang, id = "timeline-interpolation-section") {
        active?.let { CurvePreview(it) }
        PillFlow(id = "timeline-preset-categories") {
            CurvePresets.categories.forEach { name ->
                Pill("preset-category-$name", name, category == name) { category = name }
            }
        }
        PillFlow(id = "timeline-presets") {
            CurvePresets.of(category).forEach { preset ->
                Pill("preset-${preset.id}", preset.name, active?.id == preset.id) {
                    controller.applyPreset(preset)
                    refresh()
                }
            }
        }
        if (keyframe.interpolation != KeyInterpolation.BEZIER) return@Section

        Text(CutsceneLang.HANDLES.lang, modifier = Modifier.fontSize(9f).foreground(TimelineColors.Muted))
        PillFlow(id = "timeline-handle-modes") {
            HandleMode.entries.forEach { mode ->
                Pill("handle-mode-${mode.name}", handleLabel(mode), keyframe.handleMode == mode) {
                    controller.setSelectedHandleMode(mode)
                    refresh()
                }
            }
        }
        if (keyframe.handleMode == HandleMode.AUTO) {
            Text(
                CutsceneLang.HANDLES_AUTO_HINT.lang,
                modifier = Modifier.size(100.percent, UiLength.Fit)
                    .fontSize(9f)
                    .foreground(TimelineColors.Muted),
            )
            return@Section
        }
        TangentRow(controller, keyframe, TangentSide.INCOMING, CutsceneLang.HANDLE_IN.lang, refresh)
        TangentRow(controller, keyframe, TangentSide.OUTGOING, CutsceneLang.HANDLE_OUT.lang, refresh)
    }
}

private fun handleLabel(mode: HandleMode): String = when (mode) {
    HandleMode.AUTO -> CutsceneLang.HANDLES_AUTO.lang
    HandleMode.MIRRORED -> CutsceneLang.HANDLES_MIRRORED.lang
    HandleMode.ALIGNED -> CutsceneLang.HANDLES_ALIGNED.lang
    HandleMode.FREE -> CutsceneLang.HANDLES_FREE.lang
}

@Composable
private fun TangentRow(
    controller: TimelineController,
    keyframe: Keyframe,
    side: TangentSide,
    label: String,
    refresh: () -> Unit,
) {
    val tangent = keyframe.tangent(side)
    Row(modifier = Modifier.size(100.percent, UiLength.Fit).gap(4.px)) {
        FloatField(label, tangent.time) { next ->
            applyTangent(controller, keyframe, side, KeyTangent(next, keyframe.tangent(side).value))
            refresh()
        }
        FloatField("", tangent.value) { next ->
            applyTangent(controller, keyframe, side, KeyTangent(keyframe.tangent(side).time, next))
            refresh()
        }
    }
}

private fun applyTangent(
    controller: TimelineController,
    keyframe: Keyframe,
    side: TangentSide,
    tangent: KeyTangent,
) {
    controller.edit("Edit keyframe handle") {
        controller.setTangent(keyframe, side, tangent, keyframe.handleMode, timeScale = 1f, valueScale = 1f)
    }
}

@Composable
private fun WorkAreaSection(controller: TimelineController, refresh: () -> Unit) {
    Section(CutsceneLang.WORK_AREA.lang) {
        FloatField(CutsceneLang.END.lang, controller.workAreaEnd, 0.1f, Float.POSITIVE_INFINITY) { time ->
            controller.workAreaEnd = snapTimelineTime(time, currentUiKeyModifiers()).coerceAtLeast(0.1f)
            if (controller.currentTime > controller.workAreaEnd) controller.applyCurrentTime(0f)
            refresh()
        }
    }
}

@Composable
private fun EmptySection() {
    Section(CutsceneLang.SELECTION.lang) {
        Text(
            CutsceneLang.NO_SELECTION.lang,
            modifier = Modifier.size(100.percent, 22.px)
                .fontSize(11f)
                .foreground(TimelineColors.Muted)
        )
    }
}
