package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.modules.ui2.ScrollState
import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Time
import kotlinx.coroutines.Job

class TimelineController {

    val groups = mutableStateListOf<TrackGroup>()

    val currentTime = mutableStateOf(0f)
    val isPlaying = mutableStateOf(false)
    val workAreaEnd = mutableStateOf(10f)
    val playbackMode = mutableStateOf(PlaybackMode.LOOP)

    val playbackSpeed = mutableStateOf(1f)

    val pixelsPerSecond = mutableStateOf(100f)
    val scrollState = ScrollState()

    val selectedKeyframes = mutableStateListOf<Keyframe<*>>()
    val isWorkAreaSelected = mutableStateOf(false)

    val isEasingListExpanded = mutableStateOf(true)

    var activeDragKeyframe: Keyframe<*>? = null
    var isDraggingWorkAreaEnd = false
    var seekJob: Job? = null

    lateinit var iconPrev: Texture2d
    lateinit var iconPlay: Texture2d
    lateinit var iconPause: Texture2d
    lateinit var iconNext: Texture2d
    lateinit var iconZoomOut: Texture2d
    lateinit var iconZoomIn: Texture2d
    lateinit var iconPulse: Texture2d
    lateinit var iconFilm: Texture2d
    lateinit var iconCompress: Texture2d
    lateinit var visible: Texture2d
    lateinit var invisible: Texture2d
    lateinit var unlocked: Texture2d
    lateinit var locked: Texture2d
    lateinit var arrow: Texture2d


    fun addTrack(groupName: String, track: BaseAnimTrack) {
        var group = groups.find { it.nameState.value == groupName }
        if (group == null) {
            group = TrackGroup(groupName)
            groups.add(group)
        }
        group.tracks.add(track)
    }

    fun onUpdate() {
        if (isPlaying.value) {
            currentTime.set(currentTime.value + Time.deltaT * playbackSpeed.value)

            val end = workAreaEnd.value
            if (currentTime.value >= end) {
                currentTime.set(0f)
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
        return groups.flatMap { it.tracks }
    }

    fun clearSelection() {
        selectedKeyframes.clear()
        isWorkAreaSelected.set(false)
    }

    fun deleteSelectedKeyframes() {
        val allTracks = getAllTracks()

        val keysToRemove = selectedKeyframes.toList()

        allTracks.forEach { track ->
            val group = groups.find { it.tracks.contains(track) }
            val isLocked = (track is AnimTrack<*> && track.isLocked.value) || (group?.isLocked?.value == true)

            if (!isLocked) {
                val trackKeys = track.getKeysAsList()
                trackKeys.removeAll(keysToRemove.toSet())
            }
        }
        selectedKeyframes.clear()
    }

    fun duplicateKeyframe(track: BaseAnimTrack, original: Keyframe<*>): Keyframe<*> {
        @Suppress("UNCHECKED_CAST")
        val typedTrack = track as AnimTrack<Any?>
        @Suppress("UNCHECKED_CAST")
        val typedKey = original as Keyframe<Any?>

        val newKey = Keyframe(typedKey.time, typedKey.value, typedKey.easing)

        typedTrack.keyframes.add(newKey)

        selectedKeyframes.clear()
        selectedKeyframes.add(newKey)
        isWorkAreaSelected.set(false)

        return newKey
    }
}