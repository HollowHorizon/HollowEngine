package ru.hollowhorizon.hollowengine.client.render.shaders

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage

class BrokenGlassTransitionShader(cfg: Config = Config(), model: Model = Model(cfg)) :
    KslShader(model, FullscreenShaderUtil.fullscreenShaderPipelineCfg), TransitionShader {

    override var inputTexture by texture2d("tInput")
    override var targetTexture by texture2d("tTarget")
    override var progress by uniform1f("uProgress", 0f)

    var aspect by uniform1f("uAspect", 1.77f)

    class Model(cfg: Config) : KslProgram("Broken Glass Transition Shader") {
        init {
            val uv = interStageFloat2("uv")
            fullscreenQuadVertexStage(uv)

            fragmentStage {
                val hash22 = functionFloat2("hash22") {
                    val p = paramFloat2("p")
                    body {
                        val p3 = float3Var(fract(float3Value(p.x, p.y, p.x) * float3Value(0.1031f, 0.1030f, 0.0973f)))
                        val dotResult = float1Var(dot(p3, float3Value(p3.y, p3.z, p3.x) + 33.33f.const))
                        p3 += float3Value(dotResult, dotResult, dotResult)
                        return@body fract((float2Value(p3.x, p3.x) + p3.yz) * float2Value(p3.z, p3.y))
                    }
                }

                val rotate = functionFloat2("rotate") {
                    val v = paramFloat2("v")
                    val a = paramFloat1("a")
                    body {
                        val s = sin(a)
                        val c = cos(a)
                        return@body float2Value(
                            c * v.x - s * v.y,
                            s * v.x + c * v.y
                        )
                    }
                }

                val isPointInShard = functionBool1("isPointInShard") {
                    val pointUV = paramFloat2("pointUV")
                    val cellID = paramFloat2("cellID")
                    val gridRatio = paramFloat2("gridRatio") // GRID * Aspect

                    body {
                        val centerUV = pointUV * gridRatio
                        val iuv = float2Var(floor(centerUV))

                        val minDist = float1Var(10.0f.const)
                        val closestCell = float2Var(float2Value(0f, 0f))

                        fori((-1).const, 2.const) { y ->
                            val yVar = float1Var(y.toFloat1())
                            fori((-1).const, 2.const) { x ->
                                val xVar = float1Var(x.toFloat1())
                                val neighbor = iuv + float2Value(xVar, yVar)
                                val randPos = hash22(neighbor)
                                val center = neighbor + randPos

                                val diff = centerUV - center
                                val dist = length(diff)

                                `if`(dist lt minDist) {
                                    minDist set dist
                                    closestCell set neighbor
                                }
                            }
                        }
                        return@body all(closestCell eq cellID)
                    }
                }

                main {
                    val texInput = texture2d("tInput")
                    val texTarget = texture2d("tTarget")
                    val prog = uniformFloat1("uProgress")
                    val aspectRatio = uniformFloat1("uAspect")
                    val coords = uv.output

                    val gridX = 8.0f.const
                    val gridY = 6.0f.const
                    val gridRatio = float2Value(gridX * aspectRatio, gridY)

                    val finalColor = float4Var(sampleTexture(texTarget, coords))

                    val hitShard = bool1Var(false.const)

                    val currentGridID = floor(coords * gridRatio)

                    val searchY = int1Var(min(20.const, (prog * 20f.const + 2f.const).toInt1()))

                    fori(0.const, searchY) { y ->
                        `if`(!hitShard) {
                            fori((-1).const, 2.const) { x ->

                                val neighborID = currentGridID + float2Value(x.toFloat1(), y.toFloat1())
                                val randSeed = hash22(neighborID)

                                val fallStartDelay = randSeed.x * 0.4f.const
                                val effectiveTime = max(0f.const, prog * 1.5f.const - fallStartDelay)

                                val offset = float2Var(float2Value(0f, 0f))
                                val rotation = float1Var(0f.const)

                                `if`(effectiveTime gt 0f.const) {
                                    val gravity = 3.0f.const + randSeed.y * 2.0f.const
                                    offset.y set -0.5f.const * gravity * effectiveTime * effectiveTime

                                    offset.x set (randSeed.x - 0.5f.const) * 0.5f.const * effectiveTime

                                    rotation set (randSeed.x - 0.5f.const) * 4.0f.const * effectiveTime
                                }

                                val shardCenterUV = (neighborID + hash22(neighborID)) / gridRatio


                                var localUV: KslVectorExpression<KslFloat2, KslFloat1> = coords - shardCenterUV

                                localUV -= offset

                                val rotVar = float2Var(rotate(localUV, -rotation))
                                localUV = rotVar

                                val restUV = localUV + shardCenterUV

                                `if`((restUV.x ge 0f.const) and (restUV.x le 1f.const) and
                                        (restUV.y ge 0f.const) and (restUV.y le 1f.const)) {

                                    `if`(isPointInShard(restUV, neighborID, gridRatio)) {

                                        val shardColor = sampleTexture(texInput, restUV)
                                        finalColor set shardColor

                                        `if`(effectiveTime gt 0f.const) {
                                            val v = 1f.const + 0.2f.const * sin(rotation * 10f.const)
                                            finalColor.rgb *= float3Value(v, v, v)
                                        }

                                        hitShard set true.const
                                    }
                                }
                            }
                        }
                    }

                    `if`(!hitShard) {
                        `if`(prog lt 0.1f.const) {
                            finalColor set sampleTexture(texInput, coords)
                        }
                    }

                    colorOutput(finalColor)
                }
            }
        }
    }

    class Config
}