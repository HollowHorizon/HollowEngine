package ru.hollowhorizon.hollowengine.client.video

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.fml.loading.FMLPaths
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.IOUtils
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.ColorUtil
import org.jcodec.scale.RgbToBgr
import ru.hollowhorizon.hc.HollowCore
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
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

class Video(
    private val url: String, val resourceLocation: ResourceLocation, texture: VideoFrameTexture,
    var framesPerSecond: Double, muted: Boolean
) {
    private var paused = false
    private var hasAudioLoaded = false
    var isRepeat: Boolean = false

    var isMuted: Boolean = muted

    private val texture: VideoFrameTexture = texture

    private var frameGrabber: FrameGrab? = null
    private var prevFrameGrabber: FrameGrab? = null
    private var mp4FileOnDisk: File? = null
    private var startTime: Long = -1
    var lastFrame: Int = -1
        private set
    private var pausedAudioTime: Long = 0
    private var audioClip: Clip? = null

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
                    if (audioClip != null && !this.isMuted) {
                        audioClip!!.loop(-1)
                        audioClip!!.framePosition = 0
                    }
                    startTime = System.currentTimeMillis()
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
        if(frameGrabber != null) return
        try {
            val `in`: InputStream = File(url).inputStream() //URL(url).openStream()
            val path: Path = Paths.get(videoCacheFolder.toString(), resourceLocation.path+".mp4")
//            path.toFile().parentFile.mkdirs()
//            path.toFile().createNewFile()
//            Files.copy(`in`, path, StandardCopyOption.REPLACE_EXISTING)
//            `in`.close()
            mp4FileOnDisk = path.toFile()
            frameGrabber = FrameGrab.createFrameGrab(NIOUtils.readableChannel(mp4FileOnDisk))
            HollowCore.LOGGER.info("loaded mp4 video from $url")
            if (!this.isMuted) {
                setupAudio(mp4FileOnDisk, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAudio(mp4File: File?, time: Long) {
//        val aacAudioFileReader: AACAudioFileReader = AACAudioFileReader()
//        try {
//            val audioInputStream: AudioInputStream = aacAudioFileReader.getAudioInputStream(mp4File)
//            audioClip = AudioSystem.getClip()
//
//            audioClip.open(audioInputStream)
//
//            audioClip.setMicrosecondPosition(time)
//            audioClip.start()
//            if (!hasAudioLoaded) {
//                LOGGER.info("loaded mp4 audio from $url")
//            }
//            hasAudioLoaded = true
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
    }

    fun isPaused(): Boolean {
        return paused
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        if (audioClip != null && hasAudioLoaded) {
            if (paused || this.isMuted) {
                if (audioClip!!.isOpen) {
                    audioClip!!.close()
                }
            } else {
                if (!audioClip!!.isOpen) {
                    setupAudio(mp4FileOnDisk, pausedAudioTime)
                }
            }
        }
    }

    fun getMp4FileOnDisk(): File? {
        return mp4FileOnDisk
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