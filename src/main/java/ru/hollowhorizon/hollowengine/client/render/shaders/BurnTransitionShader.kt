package ru.hollowhorizon.hollowengine.client.render.shaders


import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage

class BurnTransitionShader(cfg: Config = Config(), model: Model = Model(cfg)) :
    KslShader(model, FullscreenShaderUtil.fullscreenShaderPipelineCfg), TransitionShader {

    override var inputTexture by texture2d("tInput")
    override var targetTexture by texture2d("tTarget")
    override var progress by uniform1f("uProgress", 0f)

    class Model(cfg: Config) : KslProgram("Burn Transition Shader No-Depth") {
        init {
            val uv = interStageFloat2("uv")
            fullscreenQuadVertexStage(uv)

            fragmentStage {

                val luma = functionFloat1("luma") {
                    val color = paramFloat3("color")
                    body {
                        return@body dot(color, float3Value(0.299f, 0.587f, 0.114f))
                    }
                }

                val noise = functionFloat1("noise") {
                    val coord = paramFloat2("coord")
                    body {
                        val d = dot(coord, float2Value(12.9898f, 78.233f))
                        return@body fract(sin(d) * 43758.5453f.const)
                    }
                }

                main {
                    val texInput = texture2d("tInput")
                    val texTarget = texture2d("tTarget")
                    val prog = uniformFloat1("uProgress")
                    val coords = uv.output

                    val colA = sampleTexture(texInput, coords)
                    val colB = sampleTexture(texTarget, coords)

                    val brightness = float1Var(luma(colA.rgb))

                    val n = noise(coords)
                    val burnMap = float1Var(mix(brightness, n, 0.4f.const))

                    val effectiveProgress = prog * 1.4f.const - 0.2f.const

                    val isBurned = step(burnMap, effectiveProgress)

                    val baseColor = mix(colA, colB, isBurned)

                    val dist = abs(effectiveProgress - burnMap)
                    val fireWidth = 0.1f.const // Толщина линии огня

                    val fireIntensity = float1Var(1f.const - smoothStep(0f.const, fireWidth, dist))

                    val fadeFire =
                        smoothStep(0f.const, 0.1f.const, prog) * (1f.const - smoothStep(0.9f.const, 1f.const, prog))
                    fireIntensity *= fadeFire

                    val fireColor = float4Value(1.5f, 0.6f, 0.1f, 1f)

                    val finalColor = mix(baseColor, fireColor, fireIntensity)

                    colorOutput(finalColor)
                }
            }
        }
    }

    class Config
}