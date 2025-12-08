package ru.hollowhorizon.hollowengine.client.gui.overlay

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearDepthLoad
import de.fabmax.kool.pipeline.DepthMode
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.MsdfFont
import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.kool.KoolInitEvent
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent

object ChatOverlay {
    val nickname = mutableStateOf("")
    val message = mutableStateOf("")
        .onChange { o, n ->
            surface.triggerUpdate()
        }

    lateinit var surface: UiSurface

    val scene by lazy {
        Scene().apply {
            setupUiScene()
            clearDepth = ClearDepthLoad
            depthMode = DepthMode.Legacy

            surface = addPanelSurface(IdeTheme.colors, IdeTheme.sizes.copy(normalText = MsdfFont(KoolManager.MONOCRAFT))) {
                modifier.background(null)
                    .layout(CellLayout)
                    .size(Grow.Std, Grow.Std)
                val transparency = remember { AnimatableFloat(0f) }
                val textAnimation = remember { AnimatableFloat(0f) }

                val text = message.use()

                LaunchedEffect(text) {
                    tween<Float>(0.3f, Easing.quadRev).animateTo(transparency, 1f)
                    val wordsDelay = text.trim().split(Regex("\\s+")).size * 0.6f
                    tween<Float>(1.5f, Easing.linear).animateTo(textAnimation, 1f)
                    delay((wordsDelay * 1000).toLong())
                    tween<Float>(0.3f, Easing.quadRev).animateTo(transparency, 0f)
                    textAnimation.set(0f)
                }

                Box {
                    modifier.padding(sizes.smallGap)
                        .background(
                            RoundRectBackground(
                                Color.BLACK.withAlpha(0.4f * transparency.use()),
                                sizes.smallGap
                            )
                        )
                        .border(
                            RoundRectBorder(
                                Color.WHITE.withAlpha(transparency.use()),
                                sizes.smallGap,
                                sizes.borderWidth
                            )
                        )
                        .align(AlignmentX.Center, AlignmentY.Bottom)
                        .margin(bottom = 40.dp + 100.dp * transparency.use())

                    if (text.isNotEmpty()) {
                        Row {
                            Text(nickname.use() + ": ") {
                                modifier.textColor(MdColor.BLUE.tone(80).withAlpha(transparency.use()))
                                    .align(AlignmentX.Center, AlignmentY.Center)
                                    .font(MsdfFont(KoolManager.MONOCRAFT, 20f))
                            }
                            Text(text.substring(0, (text.length * textAnimation.use()).toInt())) {
                                modifier.textColor(MdColor.GREY.tone(100).withAlpha(transparency.use()))
                                    .align(AlignmentX.Center, AlignmentY.Center)
                                    .font(MsdfFont(KoolManager.MONOCRAFT, 20f))
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onKoolInit(event: KoolInitEvent) {
        event.attachScene(scene)
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if(event.overlay == GuiOverlay.CHAT_PANEL) scene.render()
    }
}