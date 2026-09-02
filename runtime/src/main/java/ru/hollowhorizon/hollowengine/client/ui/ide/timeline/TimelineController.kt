@file:Suppress("UNCHECKED_CAST")

package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PlaybackMode {
    LOOP, ONCE
}

/** Which editor the timeline lanes are showing. */
enum class TimelineViewMode {
    /** Keys as points on their lane. */
    DOPE_SHEET,

    /** Channel values as editable curves, shaping the motion itself. */
    CURVES,
}

class TimelineController {
    companion object {
        const val KEYFRAME_TIME_EPSILON = ChannelCurve.KEY_TIME_EPSILON
        private const val DEFAULT_CURVE_SPAN = 20f
    }

    val groups = mutableStateListOf<TrackGroup>()

    var currentTime by mutableStateOf(0f)
    var isPlaying by mutableStateOf(false)
    var workAreaEnd by mutableStateOf(10f)
    val playbackMode = mutableStateOf(PlaybackMode.LOOP)
    val playbackSpeed = mutableStateOf(1f)

    var pixelsPerSecond by mutableStateOf(100f)
    var viewMode by mutableStateOf(TimelineViewMode.DOPE_SHEET)
    var headerWidth by mutableStateOf(240f)
    val curveAxis = TimelineCurveAxis(DEFAULT_CURVE_SPAN)

    val curveValueCenter: Float get() = curveAxis.center
    val curveValueSpan: Float get() = curveAxis.span

    val selectedKeyframes = mutableStateListOf<Keyframe>()

    val focusedCurves = mutableStateListOf<ChannelCurve>()
    var isWorkAreaSelected by mutableStateOf(false)
    var isCameraPreviewEnabled by mutableStateOf(false)

    var activeLayer by mutableStateOf<AnimLayer?>(null)

    val history = TimelineHistory(this)

    var onChanged: (() -> Unit)? = null
    var onTimeChanged: (() -> Unit)? = null
    var onPreviewChanged: (() -> Unit)? = null
    var captureExtraState: (() -> Any?)? = null
    var restoreExtraState: ((Any?) -> Unit)? = null

    fun group(path: List<String>): TrackGroup {
        require(path.isNotEmpty()) { "Timeline group path must not be empty" }
        var currentGroups: MutableList<TrackGroup> = groups
        var group: TrackGroup? = null
        for (name in path) {
            group = currentGroups.find { it.nameState == name } ?: TrackGroup(name).also { currentGroups.add(it) }
            currentGroups = group.children
        }
        return group ?: error("Timeline group path is empty")
    }

    fun <T> addProperty(path: List<String>, property: AnimProperty<T>): AnimProperty<T> {
        group(path).properties.add(property)
        if (property.layers.isEmpty()) property.addLayer(BASE_LAYER_NAME)
        if (activeLayer == null) activeLayer = property.layers.firstOrNull()
        return property
    }

    fun allProperties(): List<AnimProperty<*>> = groups.flatMap { it.allProperties() }

    fun allLayers(): List<AnimLayer> = allProperties().flatMap { it.layers }

    fun allCurves(): List<ChannelCurve> = allLayers().flatMap { it.channels }

    fun propertyOf(layer: AnimLayer): AnimProperty<*>? = allProperties().firstOrNull { layer in it.layers }

    fun targetLayer(property: AnimProperty<*>): AnimLayer? {
        val active = activeLayer
        if (active != null && active in property.layers) return active
        return property.layers.firstOrNull()
    }

    fun layerOf(keyframe: Keyframe): AnimLayer? = allLayers().firstOrNull { it.curveOf(keyframe) != null }

    fun curveOf(keyframe: Keyframe): ChannelCurve? = allCurves().firstOrNull { curve ->
        curve.keyframes.any { it === keyframe }
    }

    fun isLocked(layer: AnimLayer): Boolean {
        if (layer.isLocked) return true
        val owner = propertyOf(layer) ?: return false
        return groupOf(owner)?.let { isLocked(it) } == true
    }

    fun groupOf(property: AnimProperty<*>): TrackGroup? {
        fun findIn(candidates: List<TrackGroup>): TrackGroup? {
            candidates.forEach { group ->
                if (property in group.properties) return group
                findIn(group.children)?.let { return it }
            }
            return null
        }
        return findIn(groups)
    }

    private fun isLocked(group: TrackGroup): Boolean {
        if (group.isLocked) return true
        return parentOf(group)?.let { isLocked(it) } == true
    }

    private fun parentOf(group: TrackGroup): TrackGroup? {
        fun findIn(candidates: List<TrackGroup>): TrackGroup? {
            candidates.forEach { candidate ->
                if (group in candidate.children) return candidate
                findIn(candidate.children)?.let { return it }
            }
            return null
        }
        return findIn(groups)
    }

    fun onUpdate(deltaSeconds: Float) {
        if (isPlaying) {
            applyCurrentTime(currentTime + deltaSeconds * playbackSpeed.value)

            val end = workAreaEnd
            if (currentTime >= end) {
                applyCurrentTime(0f)
                if (playbackMode.value == PlaybackMode.ONCE) {
                    isPlaying = false
                }
            }
        }
        allProperties().forEach { it.update(currentTime) }
    }

    fun applyCurrentTime(time: Float) {
        currentTime = time.coerceIn(0f, workAreaEnd)
        onTimeChanged?.invoke()
    }

    fun applyCameraPreviewEnabled(isEnabled: Boolean) {
        isCameraPreviewEnabled = isEnabled
        onPreviewChanged?.invoke()
    }

    fun togglePlayback() {
        isPlaying = !isPlaying
    }

    fun isSelected(keyframe: Keyframe): Boolean = selectedKeyframes.any { it === keyframe }

    fun clearSelection() {
        selectedKeyframes.clear()
        isWorkAreaSelected = false
    }

    fun select(keys: List<Keyframe>, additive: Boolean) {
        if (!additive) selectedKeyframes.clear()
        keys.forEach { key -> if (!isSelected(key)) selectedKeyframes.add(key) }
        isWorkAreaSelected = false
    }

    fun isFocused(curve: ChannelCurve): Boolean = focusedCurves.any { it === curve }

    fun focusCurves(curves: List<ChannelCurve>, additive: Boolean) {
        val editable = curves.filter { it.spec.supportsCurveEditor }
        if (editable.isEmpty()) return
        if (additive) {
            val allFocused = editable.all { isFocused(it) }
            if (allFocused) editable.forEach { curve -> focusedCurves.removeAll { it === curve } }
            else editable.forEach { curve -> if (!isFocused(curve)) focusedCurves.add(curve) }
            return
        }
        val alreadyExactly = focusedCurves.size == editable.size && editable.all { isFocused(it) }
        focusedCurves.clear()
        if (!alreadyExactly) focusedCurves.addAll(editable)
    }

    fun selectStacked(pressed: Keyframe, stacked: List<Keyframe>, additive: Boolean) {
        if (additive) {
            toggleSelection(listOf(pressed))
            return
        }
        val stack = if (stacked.any { it === pressed }) stacked else stacked + pressed
        if (stack.size > 1 && selectedKeyframes.size == 1) {
            val current = stack.indexOfFirst { isSelected(it) }
            if (current >= 0) {
                select(listOf(stack[(current + 1) % stack.size]), additive = false)
                return
            }
        }
        if (!isSelected(pressed)) select(listOf(pressed), additive = false)
    }

    fun toggleSelection(keys: List<Keyframe>) {
        val allSelected = keys.all { isSelected(it) }
        if (allSelected) keys.forEach { key -> selectedKeyframes.removeAll { it === key } }
        else keys.forEach { key -> if (!isSelected(key)) selectedKeyframes.add(key) }
        isWorkAreaSelected = false
    }

    fun edit(label: String, block: () -> Unit) {
        history.record(label) {
            block()
            onChanged?.invoke()
        }
    }

    fun deleteSelectedKeyframes() {
        edit("Delete keyframes") {
            val doomed = selectedKeyframes.toList()
            allLayers().forEach { layer ->
                if (isLocked(layer)) return@forEach
                layer.channels.forEach { curve ->
                    curve.keyframes.removeAll { key -> doomed.any { it === key } }
                }
            }
            selectedKeyframes.clear()
        }
    }

    fun setKey(curve: ChannelCurve, time: Float, value: Float, selectKey: Boolean = true): Keyframe {
        val clamped = time.coerceIn(0f, workAreaEnd)
        val bounded = curve.spec.normalize(boundsOf(curve).clamp(value))
        val existing = curve.keyAt(clamped)
        val key = if (existing != null) {
            existing.value = bounded
            existing
        } else {
            val previous = curve.keyframes.filter { it.time <= clamped }.maxByOrNull { it.time }
            Keyframe(
                time = clamped,
                value = bounded,
                interpolation = if (curve.spec.supportsCurveEditor) {
                    previous?.interpolation ?: KeyInterpolation.BEZIER
                } else {
                    KeyInterpolation.CONSTANT
                },
            ).also { curve.keyframes.add(it) }
        }
        curve.sort()
        if (selectKey) select(listOf(key), additive = false)
        return key
    }

    fun setSelectedKeyframeValue(reference: Keyframe, value: Float) {
        val referenceCurve = curveOf(reference) ?: return
        val delta = value - reference.value
        edit("Edit keyframe value") {
            selectedKeyframes.forEach { key ->
                val layer = layerOf(key) ?: return@forEach
                if (isLocked(layer)) return@forEach
                val curve = layer.curveOf(key) ?: return@forEach
                val candidate = if (referenceCurve.spec.sampling == ChannelSampling.DISCRETE) {
                    if (curve.spec.valueOptions != referenceCurve.spec.valueOptions) return@forEach
                    value
                } else {
                    key.value + delta
                }
                key.value = curve.spec.normalize(boundsOf(curve).clamp(candidate))
            }
        }
    }

    fun addKeyframes(layer: AnimLayer, time: Float): List<Keyframe> {
        if (isLocked(layer)) return emptyList()
        return edited("Add keyframe") {
            val property = propertyOf(layer)
            layer.channels.mapIndexed { channel, curve ->
                val fallback = property?.let { defaultChannel(it, channel) } ?: 0f
                setKey(curve, time, curve.valueAt(time, fallback), selectKey = false)
            }.also { select(it, additive = false) }
        }
    }

    private fun defaultChannel(property: AnimProperty<*>, channel: Int): Float {
        val values = FloatArray(property.channels.size)
        property.decomposeDefault(values)
        return values.getOrElse(channel) { 0f }
    }

    private class KeyframeClip(val curve: ChannelCurve, val offset: Float, val state: KeyframeState)

    private var clipboard: List<KeyframeClip> = emptyList()

    val canPaste: Boolean get() = clipboard.isNotEmpty()

    fun copySelectedKeyframes() {
        val keys = selectedKeyframes.toList()
        if (keys.isEmpty()) return
        val earliest = keys.minOf { it.time }
        clipboard = keys.mapNotNull { key ->
            val curve = curveOf(key) ?: return@mapNotNull null
            KeyframeClip(curve, key.time - earliest, KeyframeState.of(key))
        }
    }

    fun cutSelectedKeyframes() {
        copySelectedKeyframes()
        deleteSelectedKeyframes()
    }

    fun pasteKeyframes(time: Float = currentTime) {
        if (clipboard.isEmpty()) return
        val live = allCurves()
        edit("Paste keyframes") {
            val created = mutableListOf<Keyframe>()
            clipboard.forEach { clip ->
                if (live.none { it === clip.curve }) return@forEach
                val layer = layerOfCurve(clip.curve)
                if (layer != null && isLocked(layer)) return@forEach
                val target = (time + clip.offset).coerceIn(0f, workAreaEnd)
                clip.curve.keyframes.removeAll { abs(it.time - target) <= KEYFRAME_TIME_EPSILON }
                val key = clip.state.toKeyframe().also {
                    it.time = target
                    it.value = boundsOf(clip.curve).clamp(it.value)
                }
                clip.curve.keyframes.add(key)
                clip.curve.sort()
                created += key
            }
            select(created, additive = false)
        }
    }

    private fun boundsOf(curve: ChannelCurve?): ChannelBounds {
        curve ?: return ChannelBounds.Unbounded
        val property = allProperties().firstOrNull { owner ->
            owner.layers.any { layer -> layer.channels.any { it === curve } }
        } ?: return ChannelBounds.Unbounded
        val channel = property.layers.firstNotNullOfOrNull { layer ->
            layer.channels.indexOfFirst { it === curve }.takeIf { it >= 0 }
        } ?: return ChannelBounds.Unbounded
        return property.bounds(channel)
    }

    private fun layerOfCurve(curve: ChannelCurve): AnimLayer? =
        allLayers().firstOrNull { layer -> layer.channels.any { it === curve } }

    fun duplicateSelectedKeyframes() {

        val originals = selectedKeyframes.toList()
        if (originals.isEmpty()) return
        edit("Duplicate keyframes") {
            val created = mutableListOf<Keyframe>()
            originals.forEach { original ->
                val curve = curveOf(original) ?: return@forEach
                val layer = layerOf(original) ?: return@forEach
                if (isLocked(layer)) return@forEach
                val target = findFreeTime(curve, original.time + KEYFRAME_TIME_EPSILON * 2f)
                if (curve.keyAt(target) != null) return@forEach
                val copy = original.copy(target)
                curve.keyframes.add(copy)
                curve.sort()
                created += copy
            }
            select(created, additive = false)
        }
    }

    var dragStartTimes: Map<Keyframe, Float>? = null
        private set
    var dragFocusKeyframe: Keyframe? = null
        private set

    var dragDriver: Keyframe? = null
        private set

    private var dragStartValues: Map<Keyframe, Float>? = null

    fun isDragDriver(keyframe: Keyframe): Boolean = dragDriver === keyframe

    fun beginKeyframeDrag(focus: Keyframe) {
        beginHistoryTransaction("Move keyframes")
        dragStartTimes = selectedKeyframes.associateWith { it.time }
        dragFocusKeyframe = focus
        dragDriver = focus
    }

    fun beginCloneDrag(driver: Keyframe, withValues: Boolean): Boolean {
        val originals = selectedKeyframes.toList().ifEmpty { listOf(driver) }
        beginHistoryTransaction("Clone keyframes")
        val clones = LinkedHashMap<Keyframe, Keyframe>()
        originals.forEach { original ->
            val curve = curveOf(original) ?: return@forEach
            val layer = layerOf(original) ?: return@forEach
            if (isLocked(layer)) return@forEach
            val target = findFreeTime(curve, original.time + KEYFRAME_TIME_EPSILON * 2f)
            if (curve.keyAt(target) != null) return@forEach
            val copy = original.copy(target)
            curve.keyframes.add(copy)
            curve.sort()
            clones[original] = copy
        }
        if (clones.isEmpty()) {
            commitHistoryTransaction()
            return false
        }
        select(clones.values.toList(), additive = false)
        dragStartTimes = clones.values.associateWith { it.time }
        if (withValues) dragStartValues = clones.values.associateWith { it.value }
        dragFocusKeyframe = clones[driver] ?: clones.values.first()
        dragDriver = driver
        onChanged?.invoke()
        return true
    }

    fun beginCurveDrag(focus: Keyframe) {
        beginKeyframeDrag(focus)
        dragStartValues = selectedKeyframes.associateWith { it.value }
    }

    fun applyKeyframeDrag(deltaSeconds: Float) {
        val starts = dragStartTimes ?: return
        if (moveKeyframesFromStarts(starts, deltaSeconds)) onChanged?.invoke()
    }

    fun applyCurveDrag(deltaSeconds: Float, deltaValue: Float) {
        val starts = dragStartTimes ?: return
        var changed = moveKeyframesFromStarts(starts, deltaSeconds)
        dragStartValues?.forEach { (keyframe, startValue) ->
            val target = boundsOf(curveOf(keyframe)).clamp(startValue + deltaValue)
            if (keyframe.value != target) {
                keyframe.value = target
                changed = true
            }
        }
        if (changed) onChanged?.invoke()
    }

    fun endKeyframeDrag() {
        dragStartTimes = null
        dragFocusKeyframe = null
        dragDriver = null
        dragStartValues = null
        commitHistoryTransaction()
    }

    fun nudgeSelectedKeyframes(deltaSeconds: Float) {
        if (selectedKeyframes.isEmpty()) return
        edit("Nudge keyframes") {
            val starts = selectedKeyframes.associateWith { it.time }
            moveKeyframesFromStarts(starts, deltaSeconds)
        }
    }

    private fun moveKeyframesFromStarts(starts: Map<Keyframe, Float>, deltaSeconds: Float): Boolean {
        if (starts.isEmpty()) return false
        val clampedDelta = deltaSeconds.coerceIn(
            minimumValue = -starts.values.min(),
            maximumValue = workAreaEnd - starts.values.max(),
        )
        val dragged = starts.keys
        val targets = starts.mapValues { (_, startTime) -> startTime + clampedDelta }
        val blocked = targets.any { (keyframe, target) ->
            curveOf(keyframe)?.keyframes?.any { other ->
                dragged.none { it === other } && abs(other.time - target) <= KEYFRAME_TIME_EPSILON
            } == true
        }
        if (blocked) return false

        var changed = false
        targets.forEach { (keyframe, target) ->
            if (keyframe.time != target) {
                keyframe.time = target
                changed = true
            }
        }
        if (changed) allCurves().forEach { it.sort() }
        return changed
    }

    fun applyPreset(preset: CurvePreset) {
        if (!canEditSelectedCurves) return
        edit("Apply curve preset") {
            selectedKeyframes.toList().forEach { key ->
                val curve = curveOf(key) ?: return@forEach
                if (!curve.spec.supportsCurveEditor) return@forEach
                val index = curve.keyframes.indexOfFirst { it === key }
                CurvePresets.apply(preset, key, curve.keyframes.getOrNull(index + 1))
            }
        }
    }

    fun setSelectedHandleMode(mode: HandleMode) {
        edit("Edit keyframe handles") {
            selectedKeyframes.forEach { key ->
                val curve = curveOf(key) ?: return@forEach
                if (!curve.spec.supportsCurveEditor) return@forEach
                if (mode != HandleMode.AUTO) freezeAutoTangents(key)
                key.handleMode = mode
            }
        }
    }

    fun setTangent(
        keyframe: Keyframe,
        side: TangentSide,
        tangent: KeyTangent,
        mode: HandleMode,
        timeScale: Float,
        valueScale: Float,
    ) {
        val curve = curveOf(keyframe) ?: return
        if (!curve.spec.supportsCurveEditor) return
        if (keyframe.handleMode == HandleMode.AUTO) freezeAutoTangents(keyframe)
        keyframe.handleMode = mode
        curve.useSpline(keyframe, side)

        val clamped = when (side) {
            TangentSide.OUTGOING -> KeyTangent(tangent.time.coerceAtLeast(0f), tangent.value)
            TangentSide.INCOMING -> KeyTangent(tangent.time.coerceAtMost(0f), tangent.value)
        }
        keyframe.setTangent(side, clamped)

        if (mode == HandleMode.FREE) {
            onChanged?.invoke()
            return
        }
        val partnerSide = if (side == TangentSide.OUTGOING) TangentSide.INCOMING else TangentSide.OUTGOING
        val partner = TimelineCurve.mirror(
            dragged = clamped,
            partner = keyframe.tangent(partnerSide),
            timeScale = timeScale,
            valueScale = valueScale,
            mirrorLength = mode == HandleMode.MIRRORED,
        )
        if (partner != null) {
            keyframe.setTangent(partnerSide, partner)
            curve.useSpline(keyframe, partnerSide)
        }
        onChanged?.invoke()
    }

    fun smoothSelectedKeyframes() {
        if (!canEditSelectedCurves) return
        edit("Smooth keyframes") {
            selectedKeyframes.forEach { key ->
                val curve = curveOf(key) ?: return@forEach
                if (!curve.spec.supportsCurveEditor) return@forEach
                key.interpolation = KeyInterpolation.BEZIER
                key.handleMode = HandleMode.AUTO
                key.incoming = KeyTangent.ZERO
                key.outgoing = KeyTangent.ZERO
            }
        }
    }

    private fun freezeAutoTangents(keyframe: Keyframe) {
        if (keyframe.handleMode != HandleMode.AUTO) return
        val curve = curveOf(keyframe) ?: return
        val tangents = curve.effectiveTangents(keyframe)
        keyframe.incoming = tangents.incoming
        keyframe.outgoing = tangents.outgoing
    }

    fun enterCurveView() {
        val wasDopeSheet = viewMode == TimelineViewMode.DOPE_SHEET
        focusedCurves.removeAll { !it.spec.supportsCurveEditor }
        viewMode = TimelineViewMode.CURVES
        if (wasDopeSheet) frameCurves()
    }

    fun frameCurves() {
        var lowest = Float.MAX_VALUE
        var highest = -Float.MAX_VALUE
        allLayers().forEach { layer ->
            if (!layer.isVisible) return@forEach
            layer.channels.forEach { curve ->
                if (!curve.isVisible) return@forEach
                if (!curve.spec.supportsCurveEditor) return@forEach
                if (focusedCurves.isNotEmpty() && !isFocused(curve)) return@forEach
                curve.keyframes.forEach { key ->
                    lowest = min(lowest, key.value)
                    highest = max(highest, key.value)
                }
            }
        }
        if (lowest > highest) {
            curveAxis.glideTo(0f, DEFAULT_CURVE_SPAN)
            return
        }
        curveAxis.glideTo((lowest + highest) * 0.5f, max(highest - lowest, 1f) * 1.4f)
    }

    val canEditSelectedCurves: Boolean
        get() = selectedKeyframes.any { key -> curveOf(key)?.spec?.supportsCurveEditor == true }

    fun beginHistoryTransaction(label: String) = history.begin(label)

    fun commitHistoryTransaction() = history.commit()

    fun undo() {
        history.undo()
        onChanged?.invoke()
        onTimeChanged?.invoke()
    }

    fun redo() {
        history.redo()
        onChanged?.invoke()
        onTimeChanged?.invoke()
    }

    fun clearHistory() = history.clear()

    internal fun createSnapshot(): TimelineSnapshot = TimelineSnapshot(
        properties = allProperties().map { property ->
            PropertySnapshot(
                property = property,
                type = property.type,
                layers = property.layers.map { layer ->
                    LayerSnapshot(
                        layer = layer,
                        state = LayerState.of(layer),
                        curves = layer.channels.map { curve -> curve.keyframes.map { KeyframeState.of(it) } },
                    )
                },
            )
        },
        currentTime = currentTime,
        workAreaEnd = workAreaEnd,
        extra = captureExtraState?.invoke(),
    )

    internal fun restoreSnapshot(snapshot: TimelineSnapshot) {
        snapshot.properties.forEach { propertySnapshot ->
            propertySnapshot.layers.forEach { layerSnapshot ->
                layerSnapshot.state.applyTo(layerSnapshot.layer)
                layerSnapshot.layer.channels.forEachIndexed { index, curve ->
                    curve.keyframes.clear()
                    layerSnapshot.curves.getOrNull(index)
                        ?.let { keys -> curve.keyframes.addAll(keys.map { it.toKeyframe() }) }
                }
            }
            propertySnapshot.property.restoreState(
                propertySnapshot.type,
                propertySnapshot.layers.map { it.layer },
            )
        }
        selectedKeyframes.clear()
        val liveCurves = allCurves()
        focusedCurves.retainAll { curve -> liveCurves.any { it === curve } }
        val liveLayers = allLayers()
        if (liveLayers.none { it === activeLayer }) activeLayer = liveLayers.firstOrNull()
        currentTime = snapshot.currentTime
        workAreaEnd = snapshot.workAreaEnd
        restoreExtraState?.invoke(snapshot.extra)
    }

    private fun <T> edited(label: String, block: () -> T): T {
        var result: T? = null
        edit(label) { result = block() }
        return result as T
    }

    private fun findFreeTime(curve: ChannelCurve, requestedTime: Float): Float {
        var time = requestedTime.coerceIn(0f, workAreaEnd)
        while (curve.keyAt(time) != null && time < workAreaEnd) {
            time = (time + KEYFRAME_TIME_EPSILON * 2f).coerceAtMost(workAreaEnd)
        }
        return time
    }
}

internal const val BASE_LAYER_NAME = "Base"

internal fun AnimProperty<*>.decomposeDefault(into: FloatArray) {
    (type as PropertyType<Any?>).decompose(defaultValue, into)
}

internal fun AnimProperty<*>.decomposeAt(time: Float): FloatArray {
    val values = FloatArray(channels.size)
    (type as PropertyType<Any?>).decompose(valueAt(time), values)
    return values
}

class TimelineCurveAxis(defaultSpan: Float) {
    var center by mutableStateOf(0f)
        private set
    var span by mutableStateOf(defaultSpan)
        private set

    var targetCenter by mutableStateOf(0f)
        private set
    var targetSpan by mutableStateOf(defaultSpan)
        private set

    private var centerVelocity = 0f
    private var spanVelocity = 0f
    private var lastFrame = 0L

    fun glideTo(center: Float, span: Float) {
        targetCenter = center
        targetSpan = span
    }

    fun snapTo(center: Float, span: Float) {
        glideTo(center, span)
        this.center = center
        this.span = span
        centerVelocity = 0f
        spanVelocity = 0f
    }

    fun advance(frameNanos: Long): Boolean {
        val previous = lastFrame
        lastFrame = frameNanos
        if (previous == 0L) return false

        val centerError = targetCenter - center
        val spanError = targetSpan - span
        val settled =
            abs(centerError) < span * 0.0005f && abs(centerVelocity) < span * 0.0005f && abs(spanError) < span * 0.0005f && abs(
                spanVelocity
            ) < span * 0.0005f
        if (settled) {
            if (center != targetCenter || span != targetSpan) {
                center = targetCenter
                span = targetSpan
                centerVelocity = 0f
                spanVelocity = 0f
                return true
            }
            return false
        }

        val dt = ((frameNanos - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
        centerVelocity += (centerError * SPRING_STIFFNESS - centerVelocity * SPRING_DAMPING) * dt
        spanVelocity += (spanError * SPRING_STIFFNESS - spanVelocity * SPRING_DAMPING) * dt
        center += centerVelocity * dt
        span = (span + spanVelocity * dt).coerceAtLeast(1e-4f)
        return true
    }

    private companion object {
        const val SPRING_STIFFNESS = 260f
        const val SPRING_DAMPING = 30f
    }
}
