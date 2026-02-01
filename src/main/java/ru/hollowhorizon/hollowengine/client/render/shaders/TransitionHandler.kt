
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ui2.setupUiScene
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addTextureMesh
import de.fabmax.kool.util.BackendScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.client.render.shaders.BrokenGlassTransitionShader
import ru.hollowhorizon.hollowengine.client.render.shaders.BurnTransitionShader
import ru.hollowhorizon.hollowengine.client.render.shaders.GlitchTransitionShader
import ru.hollowhorizon.hollowengine.client.render.shaders.TransitionShader
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent

object TransitionHandler {
    private val mc by lazy { Minecraft.getInstance() }
    private val mainScene: Texture2d by lazy { createFramebufferTexture(mc.mainRenderTarget) }

    @SubscribeEvent
    fun onBlockClick(event: PlayerInteractEvent.BlockInteract) {
        if (!event.level.isClientSide || event.hand != InteractionHand.MAIN_HAND) return

        val block = event.level.getBlockState(event.state.blockPos).block

        val targetPos = when (block) {
            Blocks.IRON_BLOCK -> Triple(0.5, -60.0, 0.5)
            Blocks.GOLD_BLOCK -> Triple(100.5, 70.0, 100.5)
            Blocks.DIAMOND_BLOCK -> Triple(-50.5, 64.0, -50.5)
            else -> return
        }

        val shader = when (block) {
            Blocks.IRON_BLOCK -> BrokenGlassTransitionShader()
            Blocks.GOLD_BLOCK -> BurnTransitionShader()
            Blocks.DIAMOND_BLOCK -> GlitchTransitionShader()
            else -> return
        }

        performTransition(shader, event.player, targetPos.first, targetPos.second + 1, targetPos.third)
    }

    @SubscribeEvent
    fun drawOverlays(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.VIGNETTE) return

        KoolManager.context.scenes.filter { it is TransitionScene }.forEach {
            it.render()
        }
    }

    private fun performTransition(
        shader: TransitionShader,
        player: net.minecraft.world.entity.player.Player,
        x: Double, y: Double, z: Double
    ) {
        BackendScope.launch {
            val textureData = mainScene.download()
            val snapshotTexture = Texture2d(textureData)

            shader.inputTexture = snapshotTexture
            shader.targetTexture = mainScene
            shader.progress = 0f

            val scene = TransitionScene(shader as KslShader)
            KoolManager.context.addScene(scene)

            delayFrames(1)

            player.setPos(x, y, z)

            animateFloat(durationSeconds = 2.5f) { progress ->
                shader.progress = progress
            }

            KoolManager.context.removeScene(scene)
            scene.release()
            snapshotTexture.release()
        }
    }

    private suspend fun delayFrames(frames: Int) {
        repeat(frames) {
            kotlinx.coroutines.yield()
        }
    }

    private suspend fun animateFloat(durationSeconds: Float, onUpdate: (Float) -> Unit) {
        val startTime = System.currentTimeMillis()
        val durationMs = (durationSeconds * 1000).toLong()

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = elapsed.toFloat() / durationMs

            if (fraction >= 1f) {
                onUpdate(1f)
                break
            }

            onUpdate(fraction)
            kotlinx.coroutines.yield()
        }
    }
}

class TransitionScene(val transitionShader: KslShader) : Scene() {
    init {
        setupUiScene()

        addTextureMesh {
            generateFullscreenQuad()
            shader = transitionShader
        }
    }
}