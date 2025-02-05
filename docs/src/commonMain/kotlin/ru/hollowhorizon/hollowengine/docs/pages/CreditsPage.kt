package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.docs.shaders.BlurImageShader

object CreditsPage: Composable {
    override fun UiScope.compose() {
        val titleFont = remember { sizes.normalText.derive(50f) }

        Text("Над документацией работали:") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        HollowHorizon()

        Divider()

        Text("Создатель мода HollowEngine и технической части этой документации.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Text("Тут должны были быть и другие люди, но мне лень кодить систему вкладок, тем более они мне с этой системой пока не помогали :(") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
    }

    private fun UiScope.HollowHorizon() {
        Text("HollowHorizon") {
            val font = remember {
                MsdfFont(
                    sizePts = 50f,
                    weight = 0.2f,
                    cutoff = 0.1f,
                    glowColor = Color("FCBA03FF")
                )
            }

            modifier
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .font(font)
        }

        Image(remember {
            Texture2d {
                Assets.loadImage2d("hollowengine:docs/titles/hollowhorizon.png").getOrThrow()
            }
        }) {
            val shader = BlurImageShader()
            modifier.imageSize(ImageSize.FitContent).alignX(AlignmentX.Center)
                .size(Grow(0.9f, Grow.Std), FitContent)
                .customShader(shader)

            modifier.onPositioned {
                modifier.imageProvider?.getTexture(uiNode.innerWidthPx, uiNode.innerHeightPx)?.let {
                    shader.image = it
                    shader.resolution = Vec2f(uiNode.innerWidthPx, uiNode.innerHeightPx)
                    shader.power = 0.25f
                }
            }
        }
    }
}