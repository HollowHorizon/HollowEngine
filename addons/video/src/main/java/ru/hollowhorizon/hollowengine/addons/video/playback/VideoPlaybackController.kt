package ru.hollowhorizon.hollowengine.addons.video.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.hollowhorizon.hollowengine.addons.video.decode.*
import ru.hollowhorizon.hollowengine.api.VideoPlaybackOptions
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class VideoPlaybackController(
    private val source: String,
    private val options: VideoPlaybackOptions,
    parentScope: CoroutineScope,
) : AutoCloseable {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job + Dispatchers.Default)
    private val videoQueue = ArrayBlockingQueue<RgbVideoFrame>(options.maxQueuedVideoFrames.coerceAtLeast(1))
    private val audioQueue = ArrayBlockingQueue<AudioChunk>(options.maxQueuedAudioChunks.coerceAtLeast(1))
    private val closed = AtomicBoolean(false)
    private val stateFlow = MutableStateFlow(VideoPlaybackState(source = source))
    private var currentFrame: RgbVideoFrame? = null

    val states: StateFlow<VideoPlaybackState> = stateFlow.asStateFlow()
    val state: VideoPlaybackState get() = stateFlow.value
    val pendingAudio: ArrayBlockingQueue<AudioChunk> get() = audioQueue

    fun start() {
        scope.launch {
            runCatching {
                val info = withContext(Dispatchers.IO) { FFmpegMedia.readInfo(source) }
                update { it.copy(info = info, status = VideoPlaybackStatus.BUFFERING) }
                launchVideoDecoder(info)
                if (info.hasAudio) launchAudioDecoder(info) else update { it.copy(audioFinished = true) }
            }.onFailure(::failUnlessCancelled)
        }
    }

    fun pollFrame(playbackSeconds: Double): RgbVideoFrame? {
        val active = currentFrame
        var next: RgbVideoFrame? = null
        while (true) {
            val candidate = videoQueue.peek() ?: break
            if (active != null && candidate.timestampSeconds > playbackSeconds + FrameLeadToleranceSeconds) break
            next?.close()
            next = videoQueue.poll()
        }
        if (next != null) {
            currentFrame?.close()
            currentFrame = next
            if (state.status == VideoPlaybackStatus.BUFFERING) update { it.copy(status = VideoPlaybackStatus.PLAYING) }
        }
        return next
    }

    fun shouldFinish(audioDrained: Boolean): Boolean {
        val current = state
        if (current.status == VideoPlaybackStatus.FAILED) return true
        if (!current.videoFinished || !current.audioFinished) return false
        if (videoQueue.isNotEmpty()) return false
        return audioDrained || current.info?.hasAudio != true
    }

    private fun launchVideoDecoder(info: VideoStreamInfo) {
        scope.launch {
            try {
                FFmpegVideoFrameDecoder(source, info, options.startSeconds).frames().collect { frame ->
                    putVideoFrame(frame)
                }
                update { it.copy(videoFinished = true) }
            } catch (error: Throwable) {
                failUnlessCancelled(error)
            }
        }
    }

    private fun launchAudioDecoder(info: VideoStreamInfo) {
        scope.launch {
            try {
                FFmpegAudioDecoder(source, info, options.startSeconds).chunks().collect { chunk ->
                    putAudioChunk(chunk)
                }
                update { it.copy(audioFinished = true) }
            } catch (error: Throwable) {
                failUnlessCancelled(error)
            }
        }
    }

    private suspend fun putVideoFrame(frame: RgbVideoFrame) {
        try {
            withContext(Dispatchers.IO) { videoQueue.put(frame) }
        } catch (error: Throwable) {
            frame.close()
            throw error
        }
    }

    private suspend fun putAudioChunk(chunk: AudioChunk) {
        try {
            withContext(Dispatchers.IO) { audioQueue.put(chunk) }
        } catch (error: Throwable) {
            chunk.close()
            throw error
        }
    }

    private fun failUnlessCancelled(error: Throwable) {
        if (error is CancellationException || closed.get()) return
        update {
            it.copy(
                status = VideoPlaybackStatus.FAILED,
                error = error,
                videoFinished = true,
                audioFinished = true
            )
        }
    }

    private fun update(transform: (VideoPlaybackState) -> VideoPlaybackState) {
        stateFlow.value = transform(stateFlow.value)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        job.cancel()
        currentFrame?.close()
        currentFrame = null
        drainVideoQueue()
        drainAudioQueue()
    }

    private fun drainVideoQueue() {
        while (true) {
            videoQueue.poll()?.close() ?: return
        }
    }

    private fun drainAudioQueue() {
        while (true) {
            audioQueue.poll()?.close() ?: return
        }
    }
}

data class VideoPlaybackState(
    val source: String,
    val info: VideoStreamInfo? = null,
    val status: VideoPlaybackStatus = VideoPlaybackStatus.OPENING,
    val videoFinished: Boolean = false,
    val audioFinished: Boolean = false,
    val error: Throwable? = null,
)

enum class VideoPlaybackStatus {
    OPENING,
    BUFFERING,
    PLAYING,
    FAILED,
}

private const val FrameLeadToleranceSeconds = 0.015
