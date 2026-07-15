package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.cutsceneViewportTextureId
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import kotlin.math.min

@Composable
fun CutsceneViewportDock() {
    Box(
        id = "cutscene-viewport",
        modifier = Modifier.size(100.percent, 100.percent)
            .background(UiColor.Black)
            .drawBehind(key = "cutscene-viewport") {
                val target = Minecraft.getInstance().mainRenderTarget
                if (target.width <= 0 || target.height <= 0) return@drawBehind
                val scale = min(size.width / target.width, size.height / target.height)
                val width = target.width * scale
                val height = target.height * scale
                drawTexture(
                    rect = UiRect((size.width - width) * 0.5f, (size.height - height) * 0.5f, width, height),
                    textureId = cutsceneViewportTextureId(),
                    flipY = true,
                )
            },
    )
}
