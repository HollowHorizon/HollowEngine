package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.FloatPropertyDriver
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.ui.ide.timeline.Vec3PropertyDriver
import kotlin.math.max
import kotlin.math.min

data class CameraPose(
    val position: Vec3f = Vec3f.ZERO,
    val rotation: Vec3f = Vec3f.ZERO,
    val fov: Float = 70f,
) {
    val pitch: Float get() = rotation.x
    val yaw: Float get() = rotation.y
    val roll: Float get() = rotation.z
}

class CutscenePlaybackController {
    private var pose = CameraPose()

    init {
        CameraCutsceneTracks.ensureRegistered()
    }

    val positionTrack = AnimTrack(
        name = "Position",
        driver = Vec3PropertyDriver("Координаты") { pose = pose.copy(position = it) },
        defaultValue = Vec3f.ZERO,
        trackColor = Color("68C783"),
    )

    val rotationTrack = AnimTrack(
        name = "Rotation",
        driver = Vec3PropertyDriver("Поворот") { pose = pose.copy(rotation = it) },
        defaultValue = Vec3f.ZERO,
        trackColor = Color("6C8CFF"),
    )

    val fovTrack = AnimTrack(
        name = "FOV",
        driver = FloatPropertyDriver { pose = pose.copy(fov = it) },
        defaultValue = 70f,
        trackColor = Color("F0B85A"),
    )

    var duration: Float = 10f
        private set

    var currentTime: Float = 0f
        private set

    var isPlaying: Boolean = false
        private set

    val currentPose: CameraPose get() = pose

    fun setupTracks(data: CutsceneData) {
        duration = max(0f, data.duration)
        currentTime = 0f
        isPlaying = false

        positionTrack.keyframes.replaceVec3Keyframes(data.findVec3Track(CameraCutsceneTracks.POSITION_ID))
        rotationTrack.keyframes.replaceVec3Keyframes(data.findVec3Track(CameraCutsceneTracks.ROTATION_ID))
        fovTrack.keyframes.replaceFloatKeyframes(data.findFloatTrack(CameraCutsceneTracks.FOV_ID))

        updateTracks()
    }

    fun play() {
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
    }

    fun stop() {
        isPlaying = false
        seek(0f)
    }

    fun seek(time: Float) {
        currentTime = time.coerceIn(0f, duration)
        updateTracks()
    }

    fun setDuration(duration: Float) {
        this.duration = max(0f, duration)
        if (currentTime > this.duration) {
            seek(this.duration)
        }
    }

    fun update(deltaSeconds: Float) {
        if (!isPlaying || duration <= 0f) return

        currentTime = min(duration, currentTime + max(0f, deltaSeconds))
        updateTracks()

        if (currentTime >= duration) {
            isPlaying = false
        }
    }

    fun toData(name: String = "New Cutscene"): CutsceneData {
        return CutsceneData(
            name = name,
            duration = duration,
            nodes = listOf(
                CutsceneNodeData(
                    id = "camera",
                    name = "Camera",
                    kind = CutsceneNodeKind.GROUP,
                    children = listOf(
                        positionTrack.toVec3Node(
                            "camera.position",
                            CameraCutsceneTracks.POSITION_ID,
                            CameraCutsceneTracks.VEC3_VALUE,
                        ),
                        rotationTrack.toVec3Node(
                            "camera.rotation",
                            CameraCutsceneTracks.ROTATION_ID,
                            CameraCutsceneTracks.VEC3_VALUE,
                        ),
                        fovTrack.toFloatNode(
                            "camera.fov",
                            CameraCutsceneTracks.FOV_ID,
                            CameraCutsceneTracks.FLOAT_VALUE,
                        ),
                    ),
                ),
            )
        )
    }

    private fun updateTracks() {
        positionTrack.update(currentTime)
        rotationTrack.update(currentTime)
        fovTrack.update(currentTime)
    }

    private fun MutableList<Keyframe<Vec3f>>.replaceVec3Keyframes(values: List<Keyframe<Vec3f>>) {
        clear()
        addAll(values)
    }

    private fun MutableList<Keyframe<Float>>.replaceFloatKeyframes(values: List<Keyframe<Float>>) {
        clear()
        addAll(values)
    }

    private fun AnimTrack<Vec3f>.toVec3Node(id: String, trackType: String, valueType: String): CutsceneNodeData {
        return CutsceneNodeData(
            id = id,
            name = nameState.value,
            kind = CutsceneNodeKind.TRACK,
            track = CutsceneTrackData(
                type = trackType,
                valueType = valueType,
                keyframes = keyframes.map { it.toTrackKeyframeData(KeyframeSnapshot::Vec3fSnapshot) },
            ),
        )
    }

    private fun AnimTrack<Float>.toFloatNode(id: String, trackType: String, valueType: String): CutsceneNodeData {
        return CutsceneNodeData(
            id = id,
            name = nameState.value,
            kind = CutsceneNodeKind.TRACK,
            track = CutsceneTrackData(
                type = trackType,
                valueType = valueType,
                keyframes = keyframes.map { it.toTrackKeyframeData(KeyframeSnapshot::FloatSnapshot) },
            ),
        )
    }

    private fun <T : Any> Keyframe<T>.toTrackKeyframeData(snapshot: (T) -> KeyframeSnapshot): CutsceneTrackKeyframeData {
        return CutsceneTrackKeyframeData(time, snapshot(value), EasingRegistry.nameOf(easing))
    }
}

object CameraCutsceneTracks {
    const val POSITION_ID = "hollowengine:camera.position"
    const val ROTATION_ID = "hollowengine:camera.rotation"
    const val FOV_ID = "hollowengine:camera.fov"
    const val VEC3_VALUE = "vec3"
    const val FLOAT_VALUE = "float"

    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true
        CutsceneTrackRegistry.register(CutsceneTrackType(POSITION_ID, Vec3f.ZERO))
        CutsceneTrackRegistry.register(CutsceneTrackType(ROTATION_ID, Vec3f.ZERO))
        CutsceneTrackRegistry.register(CutsceneTrackType(FOV_ID, 70f))
    }
}

private fun CutsceneData.findVec3Track(type: String): List<Keyframe<Vec3f>> {
    return findTrack(type)?.keyframes.orEmpty().mapNotNull { frame ->
        val value = (frame.value as? KeyframeSnapshot.Vec3fSnapshot)?.vector ?: return@mapNotNull null
        Keyframe(frame.time, value, EasingRegistry.resolve(frame.easing))
    }
}

private fun CutsceneData.findFloatTrack(type: String): List<Keyframe<Float>> {
    return findTrack(type)?.keyframes.orEmpty().mapNotNull { frame ->
        val value = (frame.value as? KeyframeSnapshot.FloatSnapshot)?.value ?: return@mapNotNull null
        Keyframe(frame.time, value, EasingRegistry.resolve(frame.easing))
    }
}

private fun CutsceneData.findTrack(type: String): CutsceneTrackData? {
    fun find(nodes: List<CutsceneNodeData>): CutsceneTrackData? {
        nodes.forEach { node ->
            val track = node.track
            if (track?.type == type) return track
            find(node.children)?.let { return it }
        }
        return null
    }

    return find(nodes)
}
