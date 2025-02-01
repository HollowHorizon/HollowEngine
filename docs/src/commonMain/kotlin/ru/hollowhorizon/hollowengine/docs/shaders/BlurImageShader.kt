package ru.hollowhorizon.hollowengine.docs.shaders

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.Ui2Shader
import de.fabmax.kool.pipeline.*

class BlurImageShader : KslShader(Model(), pipelineConfig) {
    var image by texture2d("uImageTex")
    var resolution by uniform2f("uResolution")
    var power by uniform1f("uPower", 0.15f)

    private class Model : KslProgram("UI2 image shader with edge blur") {
        init {
            val texCoords = interStageFloat2()
            val screenPos = interStageFloat2()
            val tint = interStageFloat4()
            val clipBounds = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)

            vertexStage {
                main {
                    texCoords.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                    tint.input set vertexAttribFloat4(Attribute.COLORS.name)
                    clipBounds.input set vertexAttribFloat4(Ui2Shader.ATTRIB_CLIP.name)

                    val vertexPos = float4Var(float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f))
                    screenPos.input set vertexPos.xy
                    outPosition set mvpMatrix().matrix * vertexPos
                }
            }

            fragmentStage {
                main {
                    val uv = texCoords.output
                    val resolution = uniformFloat2("uResolution")

                    val power = uniformFloat1("uPower")
                    val edgeFade = float1Var(
                        smoothStep(0f.const, power, uv.x) *
                                smoothStep(0f.const, power, uv.y) *
                                smoothStep(0f.const, power, 1f.const - uv.x) *
                                smoothStep(0f.const, power, 1f.const - uv.y)
                    )

                    val blurAmount = (1f.const - edgeFade) * 5f.const
                    val texelSize = 1f.const / resolution
                    val blurredColor = float4Var()

                    for (x in -2..2) {
                        for (y in -2..2) {
                            val offset = float2Var(float2Value(x.toFloat(), y.toFloat()) * texelSize * blurAmount)
                            blurredColor += sampleTexture(texture2d("uImageTex"), uv + offset)
                        }
                    }
                    blurredColor /= float4Value(25f, 25f, 25f, 25f)

                    val resultColor = blurredColor * edgeFade

                    `if` (all(screenPos.output gt clipBounds.output.xy) and
                            all(screenPos.output lt clipBounds.output.zw)) {
                        colorOutput(resultColor.rgb*resultColor.a, resultColor.a)
                    }.`else` {
                        discard()
                    }
                }
            }
        }
    }

    companion object {
        private val pipelineConfig = PipelineConfig(
            blendMode = BlendMode.BLEND_PREMULTIPLIED_ALPHA,
            cullMethod = CullMethod.NO_CULLING,
            depthTest = DepthCompareOp.ALWAYS
        )
    }
}
