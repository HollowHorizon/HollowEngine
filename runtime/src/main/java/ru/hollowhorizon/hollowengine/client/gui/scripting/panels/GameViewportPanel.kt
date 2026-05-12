package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.PipelineConfig
import de.fabmax.kool.scene.vertexAttrib
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.CUTSCENE_VIEWPORT
import ru.hollowhorizon.hollowengine.client.kool.rebindIfDepth
import ru.hollowhorizon.hollowengine.generated.Assets

class GameViewportPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.viewport", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.NPCS
    private var shader = ViewportImageShader()

    override fun UiScope.compose() {
        modifier.backgroundColor(Color.BLACK)
        Image {
            modifier.size(Grow.Std, Grow.Std)
                .imageSize(ImageSize.FitContent)
                .imageProvider(FlatImageProvider(CUTSCENE_VIEWPORT).mirrorY())
                .customShader(shader)
                .onPositioned {
                    x = it.leftPx
                    y = it.topPx
                    width = it.widthPx
                    height = it.heightPx
                }

            CUTSCENE_VIEWPORT.rebindIfDepth(Minecraft.getInstance().mainRenderTarget)
            shader.image = CUTSCENE_VIEWPORT
        }
    }

    companion object {
        var x = 0f
        var y = 0f
        var width = 0f
        var height = 0f
    }
}

class ViewportImageShader : KslShader(Model(), pipelineConfig) {
    var image by bindTexture2d("uImageTex")

    private class Model : KslProgram("UI2 viewport image shader") {
        init {
            val texCoords = interStageFloat2()
            val screenPos = interStageFloat2()
            val tint = interStageFloat4()
            val clipBounds = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)

            vertexStage {
                main {
                    texCoords.input set vertexAttrib(UiVertexLayout.texCoord)
                    tint.input set vertexAttrib(UiVertexLayout.color)
                    clipBounds.input set vertexAttrib(UiVertexLayout.clip)

                    val vertexPos by float4Value(vertexAttrib(UiVertexLayout.position), 1f)
                    screenPos.input set vertexPos.xy
                    outPosition set mvpMatrix().matrix * vertexPos
                }
            }

            fragmentStage {
                main {
                    val color by sampleTexture(texture2d("uImageTex"), texCoords.output) * tint.output

                    `if`(
                        all(screenPos.output gt clipBounds.output.xy) and
                                all(screenPos.output lt clipBounds.output.zw)
                    ) {
                        colorOutput(color.rgb, 1f.const)
                    }.`else` {
                        discard()
                    }
                }
            }
        }
    }

    companion object {
        private val pipelineConfig = PipelineConfig(
            blendMode = BlendMode.DISABLED,
            cullMethod = CullMethod.NO_CULLING,
            depthTest = DepthCompareOp.ALWAYS
        )
    }
}