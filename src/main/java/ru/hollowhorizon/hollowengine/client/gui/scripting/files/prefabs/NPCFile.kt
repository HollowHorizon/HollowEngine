package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IDEFile
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons

class NPCFile(path: String, bytes: ByteArray) : IDEFile(path) {
    val npcName = mutableStateOf("")
    val modelController = ModelController()

    override fun save() {

    }

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {

            Box(Grow(0.66f), Grow.Std) {
                modifier.background(
                    GridBackground(
                        Dimensions.PaddingExtraLarge,
                        1f,
                        modelController.offsetX.use(),
                        modelController.offsetY.use(),
                        Dimensions.PaddingSmall * 0.5f,
                    )
                )
                modelController()
                Text(npcName.use()) {
                    modifier
                        .font(remember {
                            MsdfFont(ColorTheme.Fonts.MONOCRAFT, 30f, weight = MsdfFont.WEIGHT_EXTRA_BOLD)
                        })
                        .margin(Dimensions.PaddingMedium)
                        .zLayer(1000)
                        .align(AlignmentX.Center, AlignmentY.Top)
                }
            }
            Column(Grow(0.33f), Grow.Std) {
                modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)

                ScrollArea(Grow.Std, Grow.Std, containerModifier = {
                    it.backgroundColor(ColorTheme.UI.BackgroundSecondary)
                }, withHorizontalScrollbar = false, vScrollbarModifier = {
                    it.colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary.withAlpha(0f),
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    ).width(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
                        .margin(bottom = Dimensions.PaddingHuge)
                }) {
                    modifier.layout(ColumnLayout).width(Grow.Std)
                    Editor()
                }
                Row {
                    modifier.padding(Dimensions.PaddingMedium)
                        .margin(Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                        .border(
                            RoundRectBorder(
                                ColorTheme.UI.BackgroundAccent,
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall * 0.5f
                            )
                        )
                        .alignX(AlignmentX.Center)

                    Image(icons.COPY) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                            .margin(Dimensions.PaddingMedium)
                            .alignY(AlignmentY.Center)
                    }

                    Text("Добавить компонент") {
                        modifier
                            .font(remember {
                                MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                            })
                            .textColor(ColorTheme.UI.WhiteReplacement)
                            .align(AlignmentX.Center, AlignmentY.Center)
                    }
                }
            }
        }
    }

    fun UiScope.Editor() {
        Text("Параметры НИПа") {
            modifier
                .font(remember {
                    MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                })
                .textColor(Color.WHITE)
                .margin(Dimensions.PaddingNormal)
                .margin(top = Dimensions.PaddingMedium)
                .align(AlignmentX.Center, AlignmentY.Center)
        }

        Category(icons.EYE, "Основная информация") {
            TextProperty("Отображаемое имя", npcName, "имя")
            TextProperty("Модель", modelController.model, "путь к модели")
            TextProperty("Масштаб", remember("1.0"), "Масштаб")

        }
    }

    fun UiScope.Category(icon: ResourceLocation, name: String, block: ColumnScope.() -> Unit) {
        Column(Grow.Std) {
            modifier.margin(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Row(Grow.Std) {
                modifier.margin(Dimensions.PaddingMedium)
                    .padding(Dimensions.PaddingMedium)

                Image(icon) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                }

                Text(name) {
                    modifier
                        .font(remember {
                            MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                        })
                        .textColor(Color.WHITE)
                        .margin(Dimensions.PaddingMedium)
                        .align(AlignmentX.Start, AlignmentY.Center)
                }
                Box(Grow.Std) {}

                Arrow(ArrowScope.ROTATION_DOWN) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(Dimensions.PaddingMedium)
                        .colors(
                            ColorTheme.UI.BackgroundAccent,
                            ColorTheme.UI.WhiteReplacement
                        ).alignY(AlignmentY.Center)
                }
            }

            Box(Grow.Std, Dimensions.PaddingSmall * 0.5f) {
                modifier.backgroundColor(ColorTheme.UI.BackgroundAccent)
            }

            Column(Grow(1f)) {
                modifier.padding(Dimensions.PaddingHuge)

                block()
            }
        }
    }

    fun UiScope.TextProperty(label: String, field: MutableStateValue<String>, hint: String = "") {
        Column(Grow.Std) {
            Text(label) {
                modifier
                    .font(remember {
                        MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                    })
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .padding(vertical = Dimensions.PaddingMedium)
                    .align(AlignmentX.Start, AlignmentY.Center)
            }

            Box(Grow.Std) {
                modifier.padding(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                    .border(
                        RoundRectBorder(
                            ColorTheme.UI.BackgroundAccent,
                            Dimensions.PaddingMedium,
                            Dimensions.PaddingSmall * 0.5f
                        )
                    )

                TextField(field.use()) {
                    modifier
                        .onChange { field.set(it) }
                        .hint(hint)
                        .width(Grow.Std)
                        .colors(
                            ColorTheme.UI.WhiteReplacement,
                            ColorTheme.UI.WhiteReplacement.withAlpha(0.5f),
                            ColorTheme.CodeWindow.Selection,
                            ColorTheme.UI.WhiteReplacement,
                            ColorTheme.UI.BackgroundAccent.withAlpha(0f),
                            ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                        )
                }
            }
        }
    }
}