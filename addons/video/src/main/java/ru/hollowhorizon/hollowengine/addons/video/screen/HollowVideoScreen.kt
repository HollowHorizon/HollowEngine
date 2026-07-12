package ru.hollowhorizon.hollowengine.addons.video.screen

import kotlinx.coroutines.CoroutineScope
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.addons.video.events.VideoPlaybackEvent
import ru.hollowhorizon.hollowengine.addons.video.playback.VideoAudioOutput
import ru.hollowhorizon.hollowengine.addons.video.playback.VideoPlaybackController
import ru.hollowhorizon.hollowengine.api.VideoPlaybackOptions
import ru.hollowhorizon.hollowengine.common.utils.literal

class HollowVideoScreen(
    private val source: String,
    private val options: VideoPlaybackOptions = VideoPlaybackOptions(),
    addonScope: CoroutineScope,
    private val onClosed: () -> Unit = {},
) : Screen("".literal) {
    private val controller = VideoPlaybackController(source, options, addonScope)
    private val audioOutput = VideoAudioOutput(volume = options.volume)
    private val textureRenderer = VideoTextureRenderer()
    private var started = false
    private var finished = false
    private var failure: Throwable? = null
    private var failurePosted = false
    private var playbackStartedAtNanos = 0L

    override fun init() {
        if (!started) {
            started = true
            audioOutput.initialize()
            controller.start()
            VideoPlaybackEvent.Started.post(VideoPlaybackEvent.Started(source))
        }
        super.init()
    }

    override fun tick() {
        processPlayback()
        super.tick()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        processPlayback()
        textureRenderer.render(width, height)
    }

    override fun shouldCloseOnEsc(): Boolean = true

    override fun removed() {
        closeResources()
        onClosed()
        super.removed()
    }

    private fun processPlayback() {
        if (finished) return
        val state = controller.state
        state.error?.let { error ->
            failure = error
            HollowEngine.LOGGER.error("Video playback failed for '{}'", source, error)
            postFailure(error)
            closeScreen()
            return
        }

        audioOutput.pump(controller.pendingAudio)
        val playbackSeconds = playbackSeconds(state.info?.hasAudio == true)
        val frame = controller.pollFrame(playbackSeconds)
        if (frame != null) {
            if (playbackStartedAtNanos == 0L) playbackStartedAtNanos = System.nanoTime()
            textureRenderer.upload(frame)
        }

        if (options.closeOnEnd && controller.shouldFinish(audioOutput.isDrained())) {
            finished = true
            VideoPlaybackEvent.Finished.post(VideoPlaybackEvent.Finished(source))
            closeScreen()
        }
    }

    private fun playbackSeconds(hasAudio: Boolean): Double {
        val fallback = fallbackPlaybackSeconds()
        return if (hasAudio) audioOutput.clockSeconds(fallback) else fallback
    }

    private fun fallbackPlaybackSeconds(): Double {
        val startedAt = playbackStartedAtNanos
        if (startedAt == 0L) return options.startSeconds
        return options.startSeconds + (System.nanoTime() - startedAt) / NanosPerSecond
    }

    private fun postFailure(error: Throwable) {
        if (failurePosted) return
        failurePosted = true
        VideoPlaybackEvent.Failed.post(VideoPlaybackEvent.Failed(source, error))
    }

    private fun closeScreen() {
        Minecraft.getInstance().setScreen(null)
    }

    private fun closeResources() {
        controller.close()
        audioOutput.close()
        textureRenderer.close()
        if (!finished) {
            failure?.let(::postFailure)
        }
    }
}

private const val NanosPerSecond = 1_000_000_000.0
