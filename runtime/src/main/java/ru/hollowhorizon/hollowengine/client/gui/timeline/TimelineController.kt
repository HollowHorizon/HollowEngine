package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.PointerEvent
import de.fabmax.kool.modules.ui2.ScrollState
import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Time
import kotlinx.coroutines.Job
import kotlin.math.abs

class TimelineController {
    companion object {
        const val KEYFRAME_TIME_EPSILON = 0.0001f
    }

    val groups = mutableStateListOf<TrackGroup>()

    val currentTime = mutableStateOf(0f)
    val isPlaying = mutableStateOf(false)
    val workAreaEnd = mutableStateOf(10f)
    val playbackMode = mutableStateOf(PlaybackMode.LOOP)

    val playbackSpeed = mutableStateOf(1f)

    val pixelsPerSecond = mutableStateOf(100f)
    val scrollState = ScrollState()
    val propertiesPanelWidth = mutableStateOf(250f)

    val selectedKeyframes = mutableStateListOf<Keyframe<*>>()
    val isWorkAreaSelected = mutableStateOf(false)
    val isCameraPreviewEnabled = mutableStateOf(false)

    val isEasingListExpanded = mutableStateOf(true)

    var activeDragKeyframe: Keyframe<*>? = null
    var isDraggingWorkAreaEnd = false
    var seekJob: Job? = null
    val history = TimelineHistory(this)

    var onChanged: (() -> Unit)? = null
    var onTimeChanged: (() -> Unit)? = null
    var onPreviewChanged: (() -> Unit)? = null
    var onTrackContextMenu: ((PointerEvent, AnimTrack<*>) -> Unit)? = null
    var onTrackHeaderContextMenu: ((PointerEvent, AnimTrack<*>) -> Unit)? = null
    var onTrackLaneContextMenu: ((PointerEvent, AnimTrack<*>) -> Unit)? = null
    var trackContextMenuTime: Float? = null

    lateinit var iconPrev: Texture2d
    lateinit var iconPlay: Texture2d
    lateinit var iconPause: Texture2d
    lateinit var iconNext: Texture2d
    lateinit var iconZoomOut: Texture2d
    lateinit var iconZoomIn: Texture2d
    lateinit var iconPulse: Texture2d
    lateinit var iconFilm: Texture2d
    lateinit var iconCompress: Texture2d
    lateinit var iconSave: Texture2d
    lateinit var iconLoad: Texture2d
    lateinit var visible: Texture2d
    lateinit var invisible: Texture2d
    lateinit var unlocked: Texture2d
    lateinit var locked: Texture2d
    lateinit var arrow: Texture2d


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
            group = currentGroups.find { it.nameState.value == name }
            if (group == null) {
                group = TrackGroup(name)
                currentGroups.add(group)
            }
            currentGroups = group.children
        }

        return group ?: error("Timeline group path is empty")
    }

    fun onUpdate() {
        if (isPlaying.value) {
            setCurrentTime(currentTime.value + Time.deltaT * playbackSpeed.value)

            val end = workAreaEnd.value
            if (currentTime.value >= end) {
                setCurrentTime(0f)
                if (playbackMode.value == PlaybackMode.ONCE) {
                    isPlaying.set(false)
                }
            }
        }

        getAllTracks().forEach { track ->
            track.update(currentTime.value)
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
        isWorkAreaSelected.set(false)
    }

    fun deleteSelectedKeyframes() {
        history.record("Delete keyframes") {
            deleteSelectedKeyframesInternal()
        }
    }

    fun setCurrentTime(time: Float) {
        currentTime.set(time.coerceIn(0f, workAreaEnd.value))
        onTimeChanged?.invoke()
    }

    fun setCameraPreviewEnabled(isEnabled: Boolean) {
        isCameraPreviewEnabled.set(isEnabled)
        onPreviewChanged?.invoke()
    }

    fun togglePlayback() {
        isPlaying.set(!isPlaying.value)
    }

    private fun deleteSelectedKeyframesInternal() {
        val allTracks = getAllTracks()

        val keysToRemove = selectedKeyframes.toList()

        allTracks.forEach { track ->
            val group = findGroup(track)
            val isLocked = (track is AnimTrack<*> && track.isLocked.value) || (group?.isLocked?.value == true)

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
            val clamped = time.coerceIn(0f, workAreaEnd.value)
            val existing = track.keyframes.firstOrNull { abs(it.time - clamped) <= KEYFRAME_TIME_EPSILON }
            if (existing != null) {
                existing.value = copyValue(value)
                selectedKeyframes.clear()
                selectedKeyframes.add(existing)
                isWorkAreaSelected.set(false)
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
        val clamped = targetTime.coerceIn(0f, workAreaEnd.value)
        if (typedTrack.hasKeyAt(clamped, except = keyframe)) return false
        keyframe.time = clamped
        onChanged?.invoke()
        return true
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
        currentTime.set(snapshot.currentTime)
        workAreaEnd.set(snapshot.workAreaEnd)
    }

    internal fun createSnapshot(): TimelineSnapshot {
        val tracks = getAnimTracksForSnapshot()
            .map { track -> track.keyframes.map { it.copyKeyframe() } }
        return TimelineSnapshot(tracks, currentTime.value, workAreaEnd.value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun duplicateKeyframeInternal(track: BaseAnimTrack, original: Keyframe<*>, targetTime: Float): Keyframe<*>? {
        @Suppress("UNCHECKED_CAST")
        val typedTrack = track as AnimTrack<Any?>
        @Suppress("UNCHECKED_CAST")
        val typedKey = original as Keyframe<Any?>

        if (typedTrack.hasKeyAt(targetTime)) return null
        val newKey = Keyframe(targetTime, copyValue(typedKey.value), typedKey.easing)

        typedTrack.keyframes.add(newKey)

        selectedKeyframes.clear()
        selectedKeyframes.add(newKey)
        isWorkAreaSelected.set(false)
        onChanged?.invoke()

        return newKey
    }

    private fun <T> addKeyframeInternal(track: AnimTrack<T>, time: Float, value: T, select: Boolean): Keyframe<T>? {
        val clamped = time.coerceIn(0f, workAreaEnd.value)
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
            isWorkAreaSelected.set(false)
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
        var time = requestedTime.coerceIn(0f, workAreaEnd.value)
        while (track.hasKeyAt(time) && time < workAreaEnd.value) {
            time = (time + KEYFRAME_TIME_EPSILON * 2f).coerceAtMost(workAreaEnd.value)
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
