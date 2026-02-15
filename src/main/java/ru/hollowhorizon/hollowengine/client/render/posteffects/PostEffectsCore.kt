package ru.hollowhorizon.hollowengine.client.render.posteffects

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.setupUiScene
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addTextureMesh
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.GlContext
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.client.render.shaders.BurnTransitionShader
import ru.hollowhorizon.hollowengine.client.render.shaders.GlitchTransitionShader

enum class BlendMode {
    ADDITIVE,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    REPLACE,
    LERP
}

data class RenderContext(
    val deltaTime: Float,
    val resolution: Vec2i,
    val time: Float,
    val frameCount: Long
)

interface PostEffect {
    var enabled: Boolean
    var intensity: Float
    var blendMode: BlendMode
    var order: Int

    fun initialize()
    fun apply(input: Texture2d, output: RenderTarget, context: RenderContext)
    fun release()
}

abstract class AbstractPostEffect : PostEffect {
    override var enabled: Boolean = true
    override var intensity: Float = 0.5f
    override var blendMode: BlendMode = BlendMode.LERP
    override var order: Int = 0

    override fun initialize() {}
    override fun release() {}
}

private class FramebufferChain(width: Int, height: Int) {
    private val targets = ArrayList<TextureTarget>(2)
    private val textures = ArrayList<Texture2d>(2)
    private var idx = 0

    init {
        repeat(2) {
            val tgt = TextureTarget(width, height, false, Minecraft.ON_OSX)
            targets += tgt
            textures += ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture(tgt)
        }
    }

    fun resize(width: Int, height: Int) {
        targets.forEach { it.resize(width, height, Minecraft.ON_OSX) }
        textures.forEach { tex ->
            val loaded = tex.gpuTexture as? LoadedTextureGl
            loaded?.apply {
                this.width = width
                this.height = height
            }
        }
    }

    fun nextTarget(): RenderTarget {
        idx = (idx + 1) % targets.size
        return targets[idx]
    }

    fun textureOf(target: RenderTarget): Texture2d {
        val i = targets.indexOf(target as TextureTarget)
        return textures[i]
    }

    val currentTexture: Texture2d
        get() = textures[idx]

    fun release() {
        textures.forEach { it.release() }
    }
}

private class PostEffectsScene : Scene() {
    init {
        addTextureMesh {
            generateFullscreenQuad()
        }
    }
}

private class SingleShaderScene(private val shader: KslShader) : Scene() {
    init {
        setupUiScene()

        addTextureMesh {
            generateFullscreenQuad()
            this.shader = this@SingleShaderScene.shader
        }
    }
}

private class FullscreenBlitScene : Scene() {
    private var currentTex: Texture2d? = null

    var shader: KslUnlitShader = makeShader(null)
        private set

    init {
        addTextureMesh {
            generateFullscreenQuad()
            this.shader = this@FullscreenBlitScene.shader
        }
    }

    fun setTexture(texture: Texture2d?) {
        if (texture === currentTex) return
        currentTex = texture
        shader = makeShader(texture)
        // update mesh shader reference
        children.filterIsInstance<de.fabmax.kool.scene.Mesh<*>>().firstOrNull()?.shader = shader
    }

    private fun makeShader(texture: Texture2d?): KslUnlitShader {
        return KslUnlitShader {
            pipeline { depthTest = DepthCompareOp.ALWAYS }
            color { textureData(texture) }
            modelCustomizer = { fullscreenQuadVertexStage(null) }
        }
    }
}

class PostEffectsPipeline(width: Int, height: Int) {
    private val effects = mutableListOf<PostEffect>()
    private val chain = FramebufferChain(width, height)
    private val blitScene = FullscreenBlitScene()

    init {
        KoolManager.context.addScene(blitScene)
    }

    fun addEffect(effect: PostEffect) {
        effects += effect
        effect.initialize()
        effects.sortBy { it.order }
    }

    fun removeEffect(effect: PostEffect) {
        effects.remove(effect)
        effect.release()
    }

    fun resize(width: Int, height: Int) {
        chain.resize(width, height)
    }

    fun render(source: Texture2d, target: RenderTarget, ctx: RenderContext) {
        var currentTex = source
        val active = effects.asSequence().filter { it.enabled && it.intensity > 0.0001f }.toList()

        if (active.isEmpty()) {
            blit(currentTex, target)
            return
        }

        active.forEachIndexed { index, effect ->
            val outTarget = if (index == active.lastIndex) target else chain.nextTarget()
            effect.apply(currentTex, outTarget, ctx)
            currentTex = if (outTarget === target) {
                ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture(target)
            } else {
                chain.textureOf(outTarget)
            }
        }
    }

    fun release() {
        effects.forEach { it.release() }
        chain.release()
    }

    private fun blit(input: Texture2d, output: RenderTarget) {
        output.bindWrite(true)
        blitScene.setTexture(input)
        GlContext.setupState()
        try {
            blitScene.render(recordState = false)
        } finally {
            GlContext.restoreState()
        }
    }
}

object PostEffectsManager {
    private var pipeline: PostEffectsPipeline? = null
    private var lastSize: Vec2i? = null

    fun initialize(width: Int, height: Int) {
        if (pipeline != null) return
        pipeline = PostEffectsPipeline(width, height)
        lastSize = Vec2i(width, height)
    }

    fun onResize(width: Int, height: Int) {
        pipeline?.resize(width, height)
        lastSize = Vec2i(width, height)
    }

    fun addEffect(effect: PostEffect) {
        pipeline?.addEffect(effect)
    }

    fun render(source: RenderTarget, ctx: RenderContext) {
        val pipe = pipeline ?: return
        val srcTex = ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture(source)
        pipe.render(srcTex, source, ctx)
    }
}

class BurnPostEffect : AbstractPostEffect() {
    private val shader = BurnTransitionShader()
    private val scene = SingleShaderScene(shader)

    init {
        order = 100
    }

    override fun apply(input: Texture2d, output: RenderTarget, context: RenderContext) {
        if (!enabled) return
        output.bindWrite(true)

        shader.inputTexture = input
        shader.targetTexture = input
        shader.progress = intensity.coerceIn(0f, 1f)

        GlContext.setupState()
        try {
            scene.render(recordState = false)
        } finally {
            GlContext.restoreState()
        }
    }

    override fun release() {
    }
}

class GlitchPostEffect : AbstractPostEffect() {
    private val shader = GlitchTransitionShader()
    private val scene = SingleShaderScene(shader)

    init {
        order = 200
    }

    override fun apply(input: Texture2d, output: RenderTarget, context: RenderContext) {
        if (!enabled) return
        output.bindWrite(true)

        shader.inputTexture = input
        shader.targetTexture = input
        shader.progress = intensity.coerceIn(0f, 1f)

        GlContext.setupState()
        try {
            scene.render(recordState = false)
        } finally {
            GlContext.restoreState()
        }
    }

    override fun release() {
    }
}

class DebugInvertPostEffect : AbstractPostEffect() {
    private val shader = KslShader(Model(), de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenShaderPipelineCfg)
    private val scene = SingleShaderScene(shader)

    init {
        order = -1000
        intensity = 1f
    }

    override fun initialize() {
        if (scene !in KoolManager.context.scenes) {
            KoolManager.context.addScene(scene)
        }
    }

    override fun apply(input: Texture2d, output: RenderTarget, context: RenderContext) {
        if (!enabled) return
        output.bindWrite(true)

        shader.texture2d("tInput").set(input)
        shader.uniform1f("uAmount").set(intensity.coerceIn(0f, 1f))

        GlContext.setupState()
        try {
            scene.render(recordState = false)
        } finally {
            GlContext.restoreState()
        }
    }

    private class Model : KslProgram("Debug Invert") {
        init {
            val uv = interStageFloat2("uv")
            fullscreenQuadVertexStage(uv)

            fragmentStage {
                main {
                    val tex = texture2d("tInput")
                    val amount = uniformFloat1("uAmount")
                    val col = float4Var(sampleTexture(tex, uv.output))
                    val inv = float4Var(float4Value(1f.const - col.r, 1f.const - col.g, 1f.const - col.b, col.a))
                    colorOutput(mix(col, inv, amount))
                }
            }
        }
    }
}


