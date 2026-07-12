package ru.hollowhorizon.hollowengine.api

import ru.hollowhorizon.hollowengine.common.addons.HollowAddonManager
import java.nio.file.Path

interface VideoApi {
    fun play(path: Path, options: VideoPlaybackOptions = VideoPlaybackOptions())

    fun play(source: String, options: VideoPlaybackOptions = VideoPlaybackOptions()) =
        play(Path.of(source), options)

    companion object {
        @Volatile
        private var legacyInstance: VideoApi? = null

        @Deprecated("Use VideoApi.find()", ReplaceWith("VideoApi.find()"))
        var INSTANCE: VideoApi?
            get() = find()
            set(value) {
                legacyInstance = value
            }

        fun find(): VideoApi? = HollowAddonManager.find() ?: legacyInstance
    }
}

data class VideoPlaybackOptions(
    val closeOnEnd: Boolean = true,
    val volume: Float = 1f,
    val startSeconds: Double = 0.0,
    val maxQueuedVideoFrames: Int = 8,
    val maxQueuedAudioChunks: Int = 24,
)
