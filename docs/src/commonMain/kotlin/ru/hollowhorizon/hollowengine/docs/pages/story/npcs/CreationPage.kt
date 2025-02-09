package ru.hollowhorizon.hollowengine.docs.pages.story.npcs

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.docs.backgroundMid
import ru.hollowhorizon.hollowengine.docs.pages.Divider
import ru.hollowhorizon.hollowengine.docs.shaders.BlurImageShader

object CreationPage : Composable {
    val creationTemplate = mutableListOf<TextLine>()

    override fun UiScope.compose() {
        val titleFont = remember { sizes.normalText.derive(50f) }

        Text("Character Creation") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Text("A sentient (or not so much) life form?") {
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

        Text("To create an NPC, you need to call the corresponding method and pass in various parameters as needed.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }

        Divider()

        Text("Template") {
            modifier.font(titleFont)
                .alignX(AlignmentX.Center)
                .textAlignX(AlignmentX.Center)
                .width(Grow(0.9f))
                .isWrapText(true)
        }

        Box { modifier.size(Grow.Std, sizes.gap) }

        Text("Here are all the possible parameters for character creation. Each of them is optional, with default values provided in this example.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }

        Column {
            modifier.padding(sizes.smallGap)
                .background(RoundRectBackground(colors.backgroundMid.mulRgb(0.5f), sizes.gap))
                .border(RoundRectBorder(Color.WHITE, sizes.gap, sizes.borderWidth))
                .alignX(AlignmentX.Center)
                .width(Grow(0.95f))

            val textHeight = creationTemplate
                .sumOf { it.spans.maxOf { it.second.font.textDimensions(it.first).height.toDouble() } } + sizes.gap.px
            TextArea(
                ListTextLineProvider(creationTemplate),
                height = Dp.fromPx(textHeight.toFloat()),
                withVerticalScrollbar = false,
                hScrollbarModifier = {it.height(sizes.smallGap)},
            ) {
                modifier.lineEndPadding(0f.dp)
            }
        }
    }
}