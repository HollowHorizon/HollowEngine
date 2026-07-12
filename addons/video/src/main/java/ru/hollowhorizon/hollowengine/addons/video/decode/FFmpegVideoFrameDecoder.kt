package ru.hollowhorizon.hollowengine.addons.video.decode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_free
import org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.avutil.av_malloc
import org.bytedeco.ffmpeg.global.avutil.av_q2d
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer

class FFmpegVideoFrameDecoder(
    private val source: String,
    private val info: VideoStreamInfo,
    private val startSeconds: Double,
    private val bufferPool: ByteBufferPool = ByteBufferPool(maxPooledBuffers = 32),
) {
    fun frames(): Flow<RgbVideoFrame> = flow {
        decode { frame -> emit(frame) }
    }.flowOn(Dispatchers.IO)

    private suspend fun decode(emitFrame: suspend (RgbVideoFrame) -> Unit) {
        val format = FFmpegMedia.openFormat(source)
        var codec: AVCodecContext? = null
        val packet = av_packet_alloc() ?: error("Failed to allocate FFmpeg video packet")
        val decoded = av_frame_alloc() ?: error("Failed to allocate FFmpeg video frame")
        val rgb = av_frame_alloc() ?: error("Failed to allocate FFmpeg RGB frame")
        var rgbBuffer: BytePointer? = null
        var scaler: SwsContext? = null

        try {
            codec = FFmpegMedia.createCodec(format, info.videoStreamIndex, open = true)
            val stream = format.streams(info.videoStreamIndex)
            val timeBaseSeconds = av_q2d(stream.time_base())
            val streamStartTimestamp = FFmpegMedia.streamStartTimestamp(stream.start_time())
            val streamStartSeconds = streamStartTimestamp * timeBaseSeconds

            if (startSeconds > 0.0) {
                val seekTimestamp = FFmpegMedia.secondsToStreamTimestamp(startSeconds, timeBaseSeconds, streamStartTimestamp)
                if (av_seek_frame(format, info.videoStreamIndex, seekTimestamp, AVSEEK_FLAG_BACKWARD) >= 0) {
                    avcodec_flush_buffers(codec)
                }
            }

            scaler = sws_getContext(
                info.width,
                info.height,
                codec.pix_fmt(),
                info.width,
                info.height,
                AV_PIX_FMT_RGB24,
                SWS_BILINEAR,
                null,
                null,
                null as DoublePointer?,
            ) ?: error("Failed to create FFmpeg RGB scaler")

            val rgbBufferSize = av_image_get_buffer_size(AV_PIX_FMT_RGB24, info.width, info.height, 1)
            rgbBuffer = BytePointer(av_malloc(rgbBufferSize.toLong())).capacity(rgbBufferSize.toLong())
            av_image_fill_arrays(rgb.data(), rgb.linesize(), rgbBuffer, AV_PIX_FMT_RGB24, info.width, info.height, 1)

            while (av_read_frame(format, packet) >= 0) {
                try {
                    if (packet.stream_index() == info.videoStreamIndex && avcodec_send_packet(codec, packet) == 0) {
                        receiveFrames(codec, decoded, rgb, scaler, rgbBuffer, timeBaseSeconds, streamStartSeconds, emitFrame)
                    }
                } finally {
                    av_packet_unref(packet)
                }
            }

            avcodec_send_packet(codec, null)
            receiveFrames(codec, decoded, rgb, scaler, rgbBuffer, timeBaseSeconds, streamStartSeconds, emitFrame)
        } finally {
            scaler?.let { sws_freeContext(it) }
            rgbBuffer?.let { av_free(it) }
            av_frame_free(decoded)
            av_frame_free(rgb)
            av_packet_free(packet)
            codec?.let { avcodec_free_context(it) }
            avformat_close_input(format)
        }
    }

    private suspend fun receiveFrames(
        codec: AVCodecContext,
        decoded: AVFrame,
        rgb: AVFrame,
        scaler: SwsContext,
        rgbBuffer: BytePointer,
        timeBaseSeconds: Double,
        streamStartSeconds: Double,
        emitFrame: suspend (RgbVideoFrame) -> Unit,
    ) {
        while (avcodec_receive_frame(codec, decoded) == 0) {
            val timestamp = FFmpegMedia.frameTimestampSeconds(decoded, timeBaseSeconds, streamStartSeconds)
            if (timestamp + FrameTimestampToleranceSeconds < startSeconds) continue
            sws_scale(scaler, decoded.data(), decoded.linesize(), 0, info.height, rgb.data(), rgb.linesize())
            val frame = copyRgbFrame(rgbBuffer, timestamp)
            try {
                emitFrame(frame)
            } catch (error: Throwable) {
                frame.close()
                throw error
            }
            currentCoroutineContext().ensureActive()
        }
    }

    private fun copyRgbFrame(source: BytePointer, timestamp: Double): RgbVideoFrame {
        val size = info.width * info.height * RgbBytesPerPixel
        val target = bufferPool.acquire(size)
        target.clear()
        val sourceBuffer = BytePointer(source).position(0).capacity(size.toLong()).asByteBuffer()
        sourceBuffer.limit(size)
        target.put(sourceBuffer)
        target.flip()
        return RgbVideoFrame(target, info.width, info.height, timestamp, bufferPool::release)
    }
}

private const val RgbBytesPerPixel = 3
private const val FrameTimestampToleranceSeconds = 0.001
