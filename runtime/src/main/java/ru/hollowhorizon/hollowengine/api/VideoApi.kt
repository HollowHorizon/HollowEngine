package ru.hollowhorizon.hollowengine.api

import ru.hollowhorizon.hollowengine.common.addons.HollowAddonManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.nio.file.Path

interface VideoApi {
    fun play(path: Path, options: VideoPlaybackOptions = VideoPlaybackOptions())

    fun play(source: String, options: VideoPlaybackOptions = VideoPlaybackOptions()) =
        play(source.fromReadablePath().toPath(), options)

    companion object {
        fun find(): VideoApi? = HollowAddonManager.find()
    }
}

data class VideoPlaybackOptions(
    val closeOnEnd: Boolean = true,
    val volume: Float = 1f,
    val startSeconds: Double = 0.0,
    val maxQueuedVideoFrames: Int = 8,
    val maxQueuedAudioChunks: Int = 24,
)
