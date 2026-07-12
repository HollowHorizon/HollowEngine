package ru.hollowhorizon.hollowengine.addons.video.decode

import java.nio.ByteBuffer

data class VideoStreamInfo(
    val width: Int,
    val height: Int,
    val durationSeconds: Double,
    val videoStreamIndex: Int,
    val audioStreamIndex: Int,
) {
    val hasAudio: Boolean get() = audioStreamIndex >= 0
}

data class RgbVideoFrame(
    val pixels: ByteBuffer,
    val width: Int,
    val height: Int,
    val timestampSeconds: Double,
    private val release: (ByteBuffer) -> Unit,
) : AutoCloseable {
    override fun close() = release(pixels)
}

data class AudioChunk(
    val pcm: ByteBuffer,
    val sampleRate: Int,
    val channels: Int,
    val timestampSeconds: Double,
    val durationSeconds: Double,
    private val release: (ByteBuffer) -> Unit,
) : AutoCloseable {
    override fun close() = release(pcm)
}