package ru.hollowhorizon.hollowengine.addons.video.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.addons.video.screen.HollowVideoScreen
import ru.hollowhorizon.hollowengine.api.VideoApi
import ru.hollowhorizon.hollowengine.api.VideoPlaybackOptions
import java.nio.file.Path

class HollowVideo(private val addonScope: CoroutineScope) : VideoApi, AutoCloseable {
    private var activeScreen: HollowVideoScreen? = null

    override fun play(path: Path, options: VideoPlaybackOptions) {
        play(VideoSourceResolver.resolve(path), options)
    }

    override fun play(source: String, options: VideoPlaybackOptions) {
        check(addonScope.isActive) { "The video addon is not active" }
        val resolvedSource = VideoSourceResolver.resolve(source)
        Minecraft.getInstance().execute {
            activeScreen = HollowVideoScreen(resolvedSource, options, addonScope) {
                activeScreen = null
            }.also(Minecraft.getInstance()::setScreen)
        }
    }

    override fun close() {
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            val screen = activeScreen
            if (screen != null && minecraft.screen === screen) minecraft.setScreen(null)
            activeScreen = null
        }
    }
}
