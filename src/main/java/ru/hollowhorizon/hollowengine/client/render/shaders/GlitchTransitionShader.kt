package ru.hollowhorizon.hollowengine.client.render.shaders

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage
import kotlin.math.PI

class GlitchTransitionShader(cfg: Config = Config(), model: Model = Model(cfg)) :
    KslShader(model, FullscreenShaderUtil.fullscreenShaderPipelineCfg), TransitionShader {

    override var inputTexture by texture2d("tInput")
    override var targetTexture by texture2d("tTarget")
    override var progress by uniform1f("uProgress", 0f)

    class Model(cfg: Config) : KslProgram("Glitch Transition Shader") {
        init {
            val uv = interStageFloat2("uv")
            fullscreenQuadVertexStage(uv)

            fragmentStage {
                val random = functionFloat1("random") {
                    val co = paramFloat2("co")
                    body {
                        val d = dot(co, float2Value(12.9898f, 78.233f))
                        return@body fract(sin(d) * 43758.5453f.const)
                    }
                }

                main {
                    val texInput = texture2d("tInput")
                    val texTarget = texture2d("tTarget")
                    val prog = uniformFloat1("uProgress")
                    val coords = uv.output

                    val dist = float1Var(smoothStep(0f.const, 1f.const, sin(prog * PI.const)))

                    val blockSize = 20f.const
                    val noiseCoord = float2Var(float2Value(floor(coords.y * blockSize), floor(prog * 10f.const)))
                    val noise = float1Var(random(noiseCoord))

                    val xOffset = float1Var((noise - 0.5f.const) * dist * 0.3f.const)

                    val rOff = float2Value(xOffset + dist * 0.05f.const, 0f.const)
                    val rFrom = sampleTexture(texInput, coords + rOff).r
                    val rTo = sampleTexture(texTarget, coords + rOff).r
                    val r = float1Var(mix(rFrom, rTo, prog))

                    val gOff = float2Value(xOffset, 0f.const)
                    val gFrom = sampleTexture(texInput, coords + gOff).g
                    val gTo = sampleTexture(texTarget, coords + gOff).g
                    val g = float1Var(mix(gFrom, gTo, prog))

                    val bOff = float2Value(xOffset - dist * 0.05f.const, 0f.const)
                    val bFrom = sampleTexture(texInput, coords + bOff).b
                    val bTo = sampleTexture(texTarget, coords + bOff).b
                    val b = float1Var(mix(bFrom, bTo, prog))

                    colorOutput(float4Value(r, g, b, 1f.const))
                }
            }
        }
    }

    class Config
}