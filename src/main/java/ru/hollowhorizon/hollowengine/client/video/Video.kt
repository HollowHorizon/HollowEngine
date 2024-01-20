package ru.hollowhorizon.hollowengine.client.video

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraftforge.fml.loading.FMLPaths
import net.sourceforge.jaad.spi.javasound.AACAudioFileReader
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.ByteBufferSeekableByteChannel
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.ColorUtil
import org.jcodec.scale.RgbToBgr
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.min


class VideoFrameTexture(image: NativeImage) : DynamicTexture(image) {
    override fun setPixels(nativeImage: NativeImage) {
        super.setPixels(nativeImage)
        if (this.pixels != null) {
            TextureUtil.prepareImage(
                this.getId(),
                pixels!!.width, pixels!!.height
            )
            this.upload()
        }
    }

    fun setPixelsFromBufferedImage(bufferedImage: BufferedImage) {
        for (i in 0 until min(pixels!!.width.toDouble(), bufferedImage.width.toDouble())
            .toInt()) {
            for (j in 0 until min(pixels!!.height.toDouble(), bufferedImage.height.toDouble())
                .toInt()) {
                val color = bufferedImage.getRGB(i, j)
                val r = color shr 16 and 255
                val g = color shr 8 and 255
                val b = color and 255
                pixels!!.setPixelRGBA(i, j, NativeImage.combine(0XFF, b, g, r))
            }
        }
        this.upload()
    }
}

enum class VideoSource(val function: (String) -> InputStream) {
    FILE({ file -> DirectoryManager.HOLLOW_ENGINE.resolve("videos/$file").inputStream() }),
    RESOURCE({ location -> location.rl.stream }),
    URL({ link ->
        val connection = URL(link).openConnection()
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"
        )
        connection.getInputStream()
    })
}

class Video(
    private val resourceType: VideoSource,
    private val resource: String,
    texture: VideoFrameTexture,
    private var framesPerSecond: Double,
    muted: Boolean
) {
    private var paused = false
    var isRepeat: Boolean = false
    var isMuted: Boolean = muted

    private val texture: VideoFrameTexture = texture

    private var frameGrabber: FrameGrab? = null
    private var prevFrameGrabber: FrameGrab? = null
    private var startTime: Long = -1
    var lastFrame: Int = -1
        private set
    private var pausedAudioTime: Long = 0
    lateinit var audioClip: Clip
    var isEnd = false
        private set

    init {
        setupFrameGrabber()
    }

    fun update() {
        if (frameGrabber != null) {
            if (prevFrameGrabber == null) {
                onStart()
            }
            val milliseconds = System.currentTimeMillis() - startTime
            val frame = (milliseconds / 1000.0 * framesPerSecond).toInt()
            pausedAudioTime = milliseconds * 1000
            if (lastFrame == frame || this.paused) {
                return
            } else {
                lastFrame = frame
            }
            try {
                val picture = frameGrabber!!.nativeFrame
                if (picture != null) {
                    val bufferedImage = toBufferedImage(picture)
                    texture.setPixelsFromBufferedImage(bufferedImage)
                } else if (isRepeat) {
                    frameGrabber!!.seekToFramePrecise(0)
                    if (!this.isMuted) {
                        audioClip.loop(-1)
                        audioClip.framePosition = 0
                    }
                    startTime = System.currentTimeMillis()
                } else {
                    isEnd = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        prevFrameGrabber = frameGrabber
    }

    fun onStart() {
        startTime = System.currentTimeMillis()
    }

    private fun setupFrameGrabber() {
        if (frameGrabber != null) return
        try {
            val bytes = resourceType.function(resource).readAllBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            frameGrabber = FrameGrab.createFrameGrab(ByteBufferSeekableByteChannel.readFromByteBuffer(buffer))
            if (!this.isMuted) setupAudio(ByteArrayInputStream(bytes), 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAudio(mp4File: InputStream, time: Long) {
        val aacAudioFileReader = AACAudioFileReader()
        try {
            val audioInputStream: AudioInputStream = aacAudioFileReader.getAudioInputStream(mp4File)
            audioClip = AudioSystem.getClip()

            audioClip.open(audioInputStream)

            audioClip.microsecondPosition = time
            audioClip.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        audioClip.stop()
    }

    companion object {

        private val videoCacheFolder: Path
            get() {
                val configPath: Path = FMLPaths.GAMEDIR.get()
                val jsonPath: Path = Paths.get(configPath.toAbsolutePath().toString(), "hollowengine/video_cache")
                if (!Files.exists(jsonPath)) {
                    try {
                        jsonPath.toFile().mkdirs()
                    } catch (e: Exception) {
                    }
                }
                return jsonPath
            }

        private fun toBufferedImage(src: Picture): BufferedImage {
            var src = src
            if (src.color != ColorSpace.BGR) {
                val bgr = Picture.createCropped(src.width, src.height, ColorSpace.BGR, src.crop)
                if (src.color == ColorSpace.RGB) {
                    RgbToBgr().transform(src, bgr)
                } else {
                    val transform = ColorUtil.getTransform(src.color, ColorSpace.RGB)
                    transform.transform(src, bgr)
                    RgbToBgr().transform(bgr, bgr)
                }
                src = bgr
            }
            val dst = BufferedImage(
                src.croppedWidth, src.croppedHeight,
                BufferedImage.TYPE_3BYTE_BGR
            )

            if (src.crop == null) toBufferedImage2(src, dst)
            else toBufferedImageCropped(src, dst)

            return dst
        }

        private fun toBufferedImageCropped(src: Picture, dst: BufferedImage) {
            val data = (dst.raster.dataBuffer as DataBufferByte).data
            val srcData = src.getPlaneData(0)
            val dstStride = dst.width * 3
            val srcStride = src.width * 3
            var line = 0
            var srcOff = 0
            var dstOff = 0
            while (line < dst.height) {
                var id = dstOff
                var `is` = srcOff
                while (id < dstOff + dstStride) {
                    data[id] = (srcData[`is`] + 128).toByte()
                    data[id + 1] = (srcData[`is` + 1] + 128).toByte()
                    data[id + 2] = (srcData[`is` + 2] + 128).toByte()
                    id += 3
                    `is` += 3
                }
                srcOff += srcStride
                dstOff += dstStride
                line++
            }
        }

        private fun toBufferedImage2(src: Picture, dst: BufferedImage) {
            val data = (dst.raster.dataBuffer as DataBufferByte).data
            val srcData = src.getPlaneData(0)
            for (i in data.indices) {
                data[i] = (srcData[i] + 128).toByte()
            }
        }
    }
}