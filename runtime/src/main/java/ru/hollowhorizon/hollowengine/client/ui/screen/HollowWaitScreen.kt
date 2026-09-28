package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.style.MinecraftHssResourceLoader

/**
 * Covers the game while something else drives it for a moment, such as a tool moving the camera to
 * take a picture, so the player neither sees the jumps nor steers into them. Its owner closes it: Esc
 * does not, and the game keeps running underneath.
 */
class HollowWaitScreen(private val heading: String, private val message: String) :
    HollowComposeUiScreen(heading, MinecraftHssResourceLoader.load(STYLESHEET)) {

    @Composable
    override fun Content() {
        Column(id = "wait-screen", tags = listOf("wait-screen")) {
            Text(heading, tags = listOf("wait-heading"))
            Text(message, tags = listOf("wait-message"))
        }
    }

    override fun shouldCloseOnEsc(): Boolean = false

    private companion object {
        const val STYLESHEET = "hollowengine:ui/styles/wait-screen.hss"
    }
}
