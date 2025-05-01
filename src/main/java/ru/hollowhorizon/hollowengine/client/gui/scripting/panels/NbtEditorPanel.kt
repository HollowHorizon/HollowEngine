package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.client.Minecraft
import net.minecraft.world.item.TooltipFlag
import ru.hollowhorizon.hc.client.kool.textLine
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.recipe.TooltipState


class NbtEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.nbt", dock) {
    val screen = (Minecraft.getInstance().screen as ScriptingEnvironmentScreen)
    override val icon: String = "hollowengine:textures/gui/icons/nbt.svg"

    private val stateText = mutableStateOf(1)

    override fun UiScope.compose() {
        modifier.margin(sizes.smallGap).width(Grow.Std).height(Grow.Std)

        val state = remember { TooltipState(0.0) }
        modifier.hoverListener(state)

        var textInArea by remember("")

        Box {
            modifier
                .size(Grow.Std, Grow.Std)
                .align(AlignmentX.Center, AlignmentY.Center)

            Column {
                modifier
                    .align(AlignmentX.Center, AlignmentY.Center)
                Row {

                    Box {
                        modifier.align(AlignmentX.Center, AlignmentY.Center).margin(end = sizes.smallGap)
                        Image(remember {
                            Texture2d {
                                Assets.loadImage2d("hollowengine:textures/gui/icons/question.png").getOrThrow()
                            }
                        }) {
                            screen.overlay = {
                                surface.popup().apply {
                                    modifier
                                        .margin(
                                            top = Dp.fromPx(state.pointerY.use()) + sizes.smallGap,
                                            start = Dp.fromPx(state.pointerX.use()) + sizes.smallGap
                                        )
                                        .onPositioned { state.popupNode = it }
                                        .layout(CellLayout)
                                        .background(UiRenderer { node ->
                                            node.apply {
                                                getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                                    .localRoundRect(
                                                        0f,
                                                        0f,
                                                        widthPx,
                                                        heightPx,
                                                        sizes.smallGap.px,
                                                        colors.background
                                                    )
                                                colors.onBackground.let {
                                                    getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                                        .localRoundRectBorder(
                                                            0f,
                                                            0f,
                                                            widthPx,
                                                            heightPx,
                                                            sizes.smallGap.px,
                                                            sizes.borderWidth.px,
                                                            it
                                                        )
                                                }
                                            }
                                        })
                                        .zLayer(UiSurface.LAYER_POPUP)
                                    Column {
                                        modifier.padding(sizes.gap)
                                        Row {
                                            Text("допустим") {
                                                modifier.font(MsdfFont(HACK_FONT, 18f))
                                                    .textColor(Color.WHITE)
                                            }

                                        }

                                    }
                                }
                            }
                        }
                    }

                    modifier.align(AlignmentX.Center, AlignmentY.Center).onDrag
                    TextField {
                        modifier
                            .backgroundColor(Color.DARK_GRAY)
                            .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))
                            .width(600.dp)
                            .height(50.dp)
                            .hint(
                                when (stateText.value) {
                                    1 -> "UUID Entity"
                                    2 -> "Позиция блока в формате x, y, z: 0, 0, 0"
                                    3 -> "пока хз как"
                                    else -> ""
                                }
                            )
                            .textAlignX(AlignmentX.Center)
                            .onChange {
                                textInArea = it
                            }
                            .text(textInArea)
                            .font(
                                MsdfFont(HACK_FONT, 20f)
                            )
                            .colors(
                                lineColor = Color(0f, 0f, 0f, 0f),
                                lineColorFocused = Color(0f, 0f, 0f, 0f)
                            )
                    }
                    Button("Получить NBT") {
                        modifier
                            .font(MsdfFont(HACK_FONT, 24f))
                            .colors(textColor = Color.GRAY, textHoverColor = Color.WHITE)
                            .margin(10.dp, 2.dp)
                    }
                }
                Row {
                    modifier
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .margin(20.dp)

                    Button("Entity") {
                        modifier
                            .onClick { stateText.value = 1 }
                            .font(MsdfFont(HACK_FONT, 30f))
                            .margin(10.dp)
                            .colors(
                                textColor = if (stateText.value == 1) Color.WHITE else Color.GRAY,
                                textHoverColor = Color.DARK_YELLOW
                            )
                    }
                    Button("Block") {
                        modifier
                            .onClick { stateText.value = 2 }
                            .font(MsdfFont(HACK_FONT, 30f))
                            .margin(10.dp)
                            .colors(
                                textColor = if (stateText.value == 2) Color.WHITE else Color.GRAY,
                                textHoverColor = Color.DARK_YELLOW
                            )
                    }
                    Button("Item") {
                        modifier
                            .onClick { stateText.value = 3 }
                            .font(MsdfFont(HACK_FONT, 30f))
                            .margin(10.dp)
                            .colors(
                                textColor = if (stateText.value == 3) Color.WHITE else Color.GRAY,
                                textHoverColor = Color.DARK_YELLOW
                            )
                    }
                }
            }
        }
    }
}