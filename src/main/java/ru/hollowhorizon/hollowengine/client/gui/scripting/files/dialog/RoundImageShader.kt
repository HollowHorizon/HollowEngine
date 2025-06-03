package ru.hollowhorizon.hollowengine.client.gui.scripting.files.dialog

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.Ui2Shader
import de.fabmax.kool.pipeline.*

class RoundImageShader : KslShader(Model(), pipelineConfig) {
    var image by texture2d("uImageTex")
    // новые uniform-параметры:
    var borderColor by uniform4f("uBorderColor") // RGBA обводки
    var borderWidth by uniform1f("uBorderWidth") // ширина обводки (в UV, 0…0.5)

    private class Model : KslProgram("UI2 image shader") {
        init {
            val texCoords = interStageFloat2()
            val screenPos = interStageFloat2()
            val tint = interStageFloat4()
            val clipBounds = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)

            vertexStage {
                main {
                    texCoords.input        set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)
                    tint.input             set vertexAttribFloat4(Attribute.COLORS.name)
                    clipBounds.input       set vertexAttribFloat4(Ui2Shader.ATTRIB_CLIP.name)

                    val vertexPos = float4Var(
                        float4Value(
                            vertexAttribFloat3(Attribute.POSITIONS.name),
                            1f
                        )
                    )
                    screenPos.input        set vertexPos.xy
                    outPosition            set mvpMatrix().matrix * vertexPos
                }
            }

            fragmentStage {
                // uniform-ы
                val uBorderColorVar = uniformFloat4("uBorderColor")
                val uBorderWidthVar = uniformFloat1("uBorderWidth")

                main {
                    // считаем UV-координаты
                    val uv = texCoords.output
                    val center = float2Value(0.5f, 0.5f)
                    val radius = 0.5f.const
                    val dist = float1Var(length(uv - center))

                    // условие: за пределы круга
                    `if`(dist gt radius) {
                        discard()
                    }

                    `if` (all(screenPos.output gt clipBounds.output.xy) and
                            all(screenPos.output lt clipBounds.output.zw)) {
                        // внутри круга — либо обводка, либо изображение
                        `if`(dist gt (radius - uBorderWidthVar)) {
                            // рисуем обводку
                            colorOutput(uBorderColorVar.xyz * uBorderColorVar.w, uBorderColorVar.w)
                        }.`else` {
                            // внутри круга: рисуем белый фон + текстуру поверх (для полупрозрачных изображений)
                            val texColor = sampleTexture(texture2d("uImageTex"), uv) * tint.output
                            // композит: white * (1 - alpha) + tex.rgb * alpha
                            val white = float3Value(1f, 1f, 1f)
                            val compRgb = white * (1f.const - texColor.a) + texColor.rgb
                            colorOutput(compRgb, 1f.const)
                        }
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