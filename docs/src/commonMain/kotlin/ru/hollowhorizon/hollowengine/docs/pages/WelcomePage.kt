package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.docs.shaders.BlurImageShader

object WelcomePage : Composable {
    override fun UiScope.compose() {
        val titleFont = remember { sizes.normalText.derive(50f) }

        Text("Добро пожаловать в HollowEngine 2.0!") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        Image(remember {
            Texture2d {
                Assets.loadImage2d("hollowengine:docs/titles/welcome.png").getOrThrow()
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
                    shader.power = 0.15f
                }
            }
        }

        Box {
            modifier.size(Grow.Std, sizes.smallGap)
                .backgroundColor(Color.WHITE)
                .margin(sizes.gap)
        }

        Text("Данная документация расскажет о базовом функционале движка HollowEngine.") {
            modifier.alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }
        Box {
            modifier.size(Grow.Std, sizes.gap)
        }
        Text("Рекомендуется перед началом работы с модом ознакомиться с базовым синтаксисом Kotlin и основными концепциями программирования в принципе.") {
            modifier.alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        Box {
            modifier.size(Grow.Std, sizes.smallGap)
                .backgroundColor(Color.WHITE)
                .margin(sizes.gap)
        }

        Text("Примечание: документация всё ещё в разработке. Других статей здесь пока нет.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
    }
}