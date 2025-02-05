package ru.hollowhorizon.hollowengine.docs.pages.story.npcs

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import ru.hollowhorizon.hollowengine.docs.backgroundMid
import ru.hollowhorizon.hollowengine.docs.pages.Divider
import ru.hollowhorizon.hollowengine.docs.shaders.BlurImageShader

object CreationPage: Composable {
    val creationTemplate = mutableListOf<TextLine>()

    override fun UiScope.compose() {
        val titleFont = remember { sizes.normalText.derive(50f) }

        Text("Создание персонажа") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Text("Разумная (или не очень) форма жизни?") {
            modifier
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        Image(remember {
            Texture2d {
                Assets.loadImage2d("hollowengine:docs/titles/npc_creation.png").getOrThrow()
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
                    shader.power = 0.20f
                }
            }
        }

        Text("Для создания нпс вам нужно вызвать одноимённый метод и передать в него разные параметры при необходимости.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }

        Divider()

        Text("Шаблон") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        Box { modifier.size(Grow.Std, sizes.gap) }

        Text("Здесь указаны все возможные параметры для создания персонажа. Каждый из них опционален, в этом примере указаны значения по умолчанию.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }

        Column {
            modifier.margin(sizes.largeGap)
                .backgroundColor(colors.backgroundMid)
            creationTemplate.forEach {
                AttributedText(it) {}
            }
        }
    }
}