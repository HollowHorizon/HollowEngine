package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.common.utils.math.Easing
import ru.hollowhorizon.hollowengine.common.utils.math.Vec2f
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlin.math.abs

class TimelineController {
    companion object {
        const val KEYFRAME_TIME_EPSILON = 0.0001f
    }

    val groups = mutableStateListOf<TrackGroup>()

    var currentTime by mutableStateOf(0f)
    var isPlaying by mutableStateOf(false)
    var workAreaEnd by mutableStateOf(10f)
    val playbackMode = mutableStateOf(PlaybackMode.LOOP)

    val playbackSpeed = mutableStateOf(1f)

    var pixelsPerSecond by mutableStateOf(100f)

    val selectedKeyframes = mutableStateListOf<Keyframe<*>>()
    var isWorkAreaSelected by mutableStateOf(false)
    var isCameraPreviewEnabled by mutableStateOf(false)

    val history = TimelineHistory(this)

    var onChanged: (() -> Unit)? = null
    var onTimeChanged: (() -> Unit)? = null
    var onPreviewChanged: (() -> Unit)? = null
    fun addTrack(groupName: String, track: BaseAnimTrack) = addTrack(listOf(groupName), track)

    fun addTrack(groupPath: List<String>, track: BaseAnimTrack) {
        require(groupPath.isNotEmpty()) { "Timeline track must belong to at least one group" }
        val group = findOrCreateGroup(groupPath)
        group.tracks.add(track)
    }

    private fun findOrCreateGroup(path: List<String>): TrackGroup {
        var currentGroups: MutableList<TrackGroup> = groups
        var group: TrackGroup? = null

        for (name in path) {
            group = currentGroups.find { it.nameState == name }
            if (group == null) {
                group = TrackGroup(name)
                currentGroups.add(group)
            }
            currentGroups = group.children
        }

        return group ?: error("Timeline group path is empty")
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

        getAllTracks().forEach { track ->
            track.update(currentTime)
        }
    }

    fun getAllTracks(): List<BaseAnimTrack> {
        return groups.flatMap { it.allTracks() }
    }

    fun findTrack(keyframe: Keyframe<*>): AnimTrack<*>? {
        return getAllTracks().filterIsInstance<AnimTrack<*>>().find { keyframe in it.keyframes }
    }

    fun clearSelection() {
        selectedKeyframes.clear()
        isWorkAreaSelected = false
    }

    fun deleteSelectedKeyframes() {
        history.record("Delete keyframes") {
            deleteSelectedKeyframesInternal()
        }
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

    private fun deleteSelectedKeyframesInternal() {
        val allTracks = getAllTracks()

        val keysToRemove = selectedKeyframes.toList()

        allTracks.forEach { track ->
            val group = findGroup(track)
            val isLocked = track is AnimTrack<*> && track.isLocked || group?.isLocked == true

            if (!isLocked) {
                val trackKeys = track.getKeysAsList()
                trackKeys.removeAll(keysToRemove.toSet())
            }
        }
        selectedKeyframes.clear()
        onChanged?.invoke()
    }

    fun duplicateKeyframe(track: BaseAnimTrack, original: Keyframe<*>): Keyframe<*> {
        return history.record("Duplicate keyframe") {
            val sourceTrack = track as? AnimTrack<*> ?: return@record null
            val targetTime = findFreeTime(sourceTrack, original.time + KEYFRAME_TIME_EPSILON * 2f)
            duplicateKeyframeInternal(track, original, targetTime)
        } ?: original
    }

    fun duplicateKeyframe(track: BaseAnimTrack, original: Keyframe<*>, targetTime: Float): Keyframe<*>? {
        return history.record("Duplicate keyframe") {
            duplicateKeyframeInternal(track, original, targetTime)
        }
    }

    fun addKeyframe(track: AnimTrack<*>, time: Float): Keyframe<*>? {
        @Suppress("UNCHECKED_CAST")
        val typedTrack = track as AnimTrack<Any?>
        return addKeyframe(typedTrack, time, typedTrack.getInsertionValue(time))
    }

    fun <T> addKeyframe(track: AnimTrack<T>, time: Float, value: T): Keyframe<T>? {
        return history.record("Add keyframe") {
            addKeyframeInternal(track, time, value, select = true)
        }
    }

    fun <T> upsertKeyframe(track: AnimTrack<T>, time: Float, value: T): Keyframe<T> {
        return history.record("Capture keyframe") {
            val clamped = time.coerceIn(0f, workAreaEnd)
            val existing = track.keyframes.firstOrNull { abs(it.time - clamped) <= KEYFRAME_TIME_EPSILON }
            if (existing != null) {
                existing.value = copyValue(value)
                selectedKeyframes.clear()
                selectedKeyframes.add(existing)
                isWorkAreaSelected = false
                onChanged?.invoke()
                existing
            } else {
                addKeyframeInternal(track, clamped, value, select = true) ?: error("Unable to create keyframe")
            }
        }
    }

    fun updateSelectedValues(label: String, block: () -> Unit) {
        history.record(label) {
            block()
            onChanged?.invoke()
        }
    }

    fun moveKeyframe(track: BaseAnimTrack, keyframe: Keyframe<*>, targetTime: Float): Boolean {
        val typedTrack = track as? AnimTrack<*> ?: return false
        val clamped = targetTime.coerceIn(0f, workAreaEnd)
        if (typedTrack.hasKeyAt(clamped, except = keyframe)) return false
        keyframe.time = clamped
        onChanged?.invoke()
        return true
    }

    /** Times of every selected keyframe captured at drag start, so a drag can move the whole group. */
    var dragStartTimes: Map<Keyframe<*>, Float>? = null
        private set
    var dragFocusKeyframe: Keyframe<*>? = null
        private set

    fun beginKeyframeDrag(focus: Keyframe<*>) {
        beginHistoryTransaction("Move keyframes")
        dragStartTimes = selectedKeyframes.associateWith { it.time }
        dragFocusKeyframe = focus
    }

    /**
     * Moves every dragged keyframe to `start + [deltaSeconds]` (clamped to the work area). The delta is
     * the total offset from the grab point, not an increment, so hitting a bound and coming back stays
     * in sync with the cursor. A move is skipped only when a non-dragged key already holds that slot.
     */
    fun applyKeyframeDrag(deltaSeconds: Float) {
        val starts = dragStartTimes ?: return
        if (moveKeyframesFromStarts(starts, deltaSeconds)) onChanged?.invoke()
    }

    fun endKeyframeDrag() {
        dragStartTimes = null
        dragFocusKeyframe = null
        commitHistoryTransaction()
    }

    /** Shifts every selected keyframe by [deltaSeconds] (clamped), recorded as one undo step. */
    fun nudgeSelectedKeyframes(deltaSeconds: Float) {
        if (selectedKeyframes.isEmpty()) return
        history.record("Nudge keyframes") {
            val starts = selectedKeyframes.associateWith { it.time }
            if (moveKeyframesFromStarts(starts, deltaSeconds)) onChanged?.invoke()
        }
    }

    private fun moveKeyframesFromStarts(starts: Map<Keyframe<*>, Float>, deltaSeconds: Float): Boolean {
        if (starts.isEmpty()) return false
        val clampedDelta = deltaSeconds.coerceIn(
            minimumValue = -starts.values.min(),
            maximumValue = workAreaEnd - starts.values.max(),
        )
        val dragged = starts.keys
        val targets = starts.mapValues { (_, startTime) -> startTime + clampedDelta }
        val blocked = targets.any { (keyframe, target) ->
            findTrack(keyframe)?.keyframes?.any { other ->
                other !in dragged && abs(other.time - target) <= KEYFRAME_TIME_EPSILON
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
        return changed
    }

    /** Duplicates every selected keyframe just after itself, selecting the new copies. */
    @Suppress("UNCHECKED_CAST")
    fun duplicateSelectedKeyframes() {
        val originals = selectedKeyframes.toList()
        if (originals.isEmpty()) return
        history.record("Duplicate keyframes") {
            val created = mutableListOf<Keyframe<*>>()
            for (original in originals) {
                val track = findTrack(original) ?: continue
                val target = findFreeTime(track, original.time + KEYFRAME_TIME_EPSILON * 2f)
                val typedTrack = track as AnimTrack<Any?>
                if (typedTrack.hasKeyAt(target)) continue
                val typedKey = original as Keyframe<Any?>
                val copy = Keyframe(target, copyValue(typedKey.value), typedKey.easing)
                typedTrack.keyframes.add(copy)
                created += copy
            }
            selectedKeyframes.clear()
            selectedKeyframes.addAll(created)
            isWorkAreaSelected = false
            onChanged?.invoke()
        }
    }

    fun beginHistoryTransaction(label: String) {
        history.begin(label)
    }

    fun commitHistoryTransaction() {
        history.commit()
    }

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

    fun clearHistory() {
        history.clear()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun restoreSnapshot(snapshot: TimelineSnapshot) {
        val tracks = getAnimTracksForSnapshot()
        snapshot.tracks.forEachIndexed { index, keyframes ->
            val track = tracks.getOrNull(index) ?: return@forEachIndexed
            track.keyframes.clear()
            track.keyframes.addAll(keyframes.map { it.copyKeyframe() })
        }
        selectedKeyframes.clear()
        currentTime = snapshot.currentTime
        workAreaEnd = snapshot.workAreaEnd
    }

    internal fun createSnapshot(): TimelineSnapshot {
        val tracks = getAnimTracksForSnapshot()
            .map { track -> track.keyframes.map { it.copyKeyframe() } }
        return TimelineSnapshot(tracks, currentTime, workAreaEnd)
    }

    @Suppress("UNCHECKED_CAST")
    private fun duplicateKeyframeInternal(
        track: BaseAnimTrack,
        original: Keyframe<*>,
        targetTime: Float,
    ): Keyframe<*>? {
        @Suppress("UNCHECKED_CAST")
        val typedTrack = track as AnimTrack<Any?>

        @Suppress("UNCHECKED_CAST")
        val typedKey = original as Keyframe<Any?>

        if (typedTrack.hasKeyAt(targetTime)) return null
        val newKey = Keyframe(targetTime, copyValue(typedKey.value), typedKey.easing)

        typedTrack.keyframes.add(newKey)

        selectedKeyframes.clear()
        selectedKeyframes.add(newKey)
        isWorkAreaSelected = false
        onChanged?.invoke()

        return newKey
    }

    private fun <T> addKeyframeInternal(track: AnimTrack<T>, time: Float, value: T, select: Boolean): Keyframe<T>? {
        val clamped = time.coerceIn(0f, workAreaEnd)
        if (track.hasKeyAt(clamped)) return null

        val easing = track.keyframes
            .filter { it.time <= clamped }
            .maxByOrNull { it.time }
            ?.easing ?: Easing.linear
        val newKey = Keyframe(clamped, copyValue(value), easing)
        track.keyframes.add(newKey)

        if (select) {
            selectedKeyframes.clear()
            selectedKeyframes.add(newKey)
            isWorkAreaSelected = false
        }
        onChanged?.invoke()
        return newKey
    }

    private fun TrackGroup.allTracks(): List<BaseAnimTrack> {
        return tracks + children.flatMap { it.allTracks() }
    }

    private fun findGroup(track: BaseAnimTrack): TrackGroup? {
        fun findIn(groups: List<TrackGroup>): TrackGroup? {
            groups.forEach { group ->
                if (track in group.tracks) return group
                findIn(group.children)?.let { return it }
            }
            return null
        }

        return findIn(groups)
    }

    private fun AnimTrack<*>.hasKeyAt(time: Float, except: Keyframe<*>? = null): Boolean {
        return keyframes.any { it !== except && abs(it.time - time) <= KEYFRAME_TIME_EPSILON }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAnimTracksForSnapshot(): List<AnimTrack<Any?>> {
        return getAllTracks()
            .filterIsInstance<AnimTrack<*>>()
            .map { it as AnimTrack<Any?> }
    }

    private fun findFreeTime(track: AnimTrack<*>, requestedTime: Float): Float {
        var time = requestedTime.coerceIn(0f, workAreaEnd)
        while (track.hasKeyAt(time) && time < workAreaEnd) {
            time = (time + KEYFRAME_TIME_EPSILON * 2f).coerceAtMost(workAreaEnd)
        }
        return time
    }
}

data class TimelineSnapshot(
    val tracks: List<List<Keyframe<Any?>>>,
    val currentTime: Float,
    val workAreaEnd: Float,
)

class TimelineHistory(private val controller: TimelineController) {
    private val undoStack = ArrayDeque<TimelineSnapshot>()
    private val redoStack = ArrayDeque<TimelineSnapshot>()
    private var transactionStart: TimelineSnapshot? = null

    fun begin(label: String) {
        if (transactionStart == null) {
            transactionStart = controller.createSnapshot()
        }
    }

    fun commit() {
        val before = transactionStart ?: return
        transactionStart = null
        val after = controller.createSnapshot()
        if (before != after) {
            undoStack += before
            redoStack.clear()
        }
    }

    fun <T> record(label: String, block: () -> T): T {
        if (transactionStart != null) {
            return block()
        }
        val before = controller.createSnapshot()
        val result = block()
        val after = controller.createSnapshot()
        if (before != after) {
            undoStack += before
            redoStack.clear()
        }
        return result
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack += controller.createSnapshot()
        controller.restoreSnapshot(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack += controller.createSnapshot()
        controller.restoreSnapshot(snapshot)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        transactionStart = null
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Keyframe<T>.copyKeyframe(): Keyframe<Any?> {
    return Keyframe(time, copyValue(value), easing)
}

@Suppress("UNCHECKED_CAST")
private fun <T> copyValue(value: T): T {
    return when (value) {
        is Vec2f -> Vec2f(value.x, value.y) as T
        is Vec3f -> Vec3f(value.x, value.y, value.z) as T
        else -> value
    }
}
