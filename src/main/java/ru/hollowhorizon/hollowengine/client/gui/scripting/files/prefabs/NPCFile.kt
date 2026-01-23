package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IDEFile
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.geary.components.AdvancedModelComponent
import ru.hollowhorizon.hollowengine.common.geary.components.GenericEditor
import ru.hollowhorizon.hollowengine.common.geary.components.InteractionComponent
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent

class NPCFile(path: String, bytes: ByteArray) : IDEFile(path) {
    val npcName = mutableStateOf("")
    val modelController = ModelController()
    val isGridVisible = mutableStateOf<Boolean>(true)

    override fun save() {

    }

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {

            Box(Grow(0.66f), Grow.Std) {

                val lineColor by animateColorAsState(
                    if (isGridVisible.use()) ColorTheme.UI.BackgroundElements.withAlpha(0.65f)
                    else ColorTheme.UI.BackgroundElements.withAlpha(0f)
                )
                modifier.background(
                    GridBackground(
                        Dimensions.PaddingExtraLarge,
                        1f,
                        modelController.scrollState,
                        Dimensions.PaddingSmall * 0.5f,
                        lineColor
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
                EditorButtons()
                EditorInfo()
            }
            Column(Grow(0.33f), Grow.Std) {
                modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)

                ScrollArea(Grow.Std, Grow.Std, containerModifier = {
                    it.backgroundColor(ColorTheme.UI.BackgroundSecondary)
                        .margin(end = Dimensions.PaddingMedium)
                }, withHorizontalScrollbar = false, vScrollbarModifier = {
                    it.colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary.withAlpha(0f),
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    ).width(Dimensions.PaddingMedium)
                }) {
                    modifier.layout(ColumnLayout).width(Grow.Std)
                        .margin(end = Dimensions.PaddingMedium)
                    Editor()
                }
                Box(Grow.Std) {
                    modifier
                        .margin(Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.Accents.Main, Dimensions.PaddingMedium))

                    Row {
                        modifier.alignX(AlignmentX.Center)

                        Image(icons.ADD) {
                            modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                                .margin(Dimensions.PaddingMedium)
                                .align(AlignmentX.Center, AlignmentY.Center)
                        }

                        Text("Добавить компонент") {
                            modifier
                                .font(remember {
                                    MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f)
                                })
                                .textColor(Color.WHITE)
                                .align(AlignmentX.Center, AlignmentY.Center)
                        }
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

        GenericEditor(remember { mutableStateOf(Model("hollowengine:models/entity/player_model.gltf", 1f)) })
        GenericEditor(remember { mutableStateOf(TransformComponent()) })
        GenericEditor(remember { mutableStateOf(InteractionComponent()) })
        GenericEditor(remember { mutableStateOf(AdvancedModelComponent()) })



//        Category(icons.EYE, "Основная информация") {
//            TextProperty("Отображаемое имя", npcName, "имя")
//            TextProperty("Модель", modelController.model, "путь к модели")
//            TextProperty("Масштаб", remember("1.0"), "Масштаб")
//        }
//        Category(icons.INTERACTION, "Взаимодействие") {
//            BoolProperty("Можно взаимодействовать?", remember(false))
//            TextProperty("Триггер при взаимодействии", remember("interact.bc"), "путь к скрипту")
//        }
//        Category(icons.BOX, "Выпадающие предметы") {
//            TextProperty("Опыт за убийство", remember("10"), "количество очков опыта")
//            ItemListProperty()
//        }
    }

    fun UiScope.EditorButtons() {
        Row {
            modifier.align(AlignmentX.Start, AlignmentY.Top)
                .zLayer(1000)

            Toggle(icons.AUTOCOMPLETE_CLASS, remember(false))
            Toggle(icons.LAYERS, remember(false))
            Toggle(icons.RECIPES, isGridVisible)
            Toggle(icons.RELOAD, remember(false))
        }
    }

    fun UiScope.EditorInfo() {
        Row {
            modifier.align(AlignmentX.End, AlignmentY.Top)
                .margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )
                .zLayer(1000)

            modelController.Info()
        }
    }

    fun UiScope.Toggle(icon: ResourceLocation, selected: MutableStateValue<Boolean>) {
        Box {
            modifier.margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)

            modifier.onClick {
                selected.set(!selected.value)
            }

            val borderColor by animateColorAsState(
                if (selected.use()) ColorTheme.Accents.Main
                else ColorTheme.UI.BackgroundAccent
            )
            val backgroundColor by animateColorAsState(
                if (selected.use()) ColorTheme.UI.BackgroundElements.mix(
                    ColorTheme.Accents.Main, 0.5f
                ) else ColorTheme.UI.BackgroundElements
            )

            modifier.background(RoundRectBackground(backgroundColor, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        borderColor,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .tint(if (selected.use()) Color.WHITE else ColorTheme.UI.WhiteReplacement)
            }
        }
    }


    fun UiScope.ItemListProperty() {
        ItemProperty(ItemStack(Items.DIRT, 5))
        ItemProperty(ItemStack(Items.DIAMOND, 3))
        ItemProperty(ItemStack(Items.END_ROD, 1))
        Row(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .margin(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Image(icons.ADD) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(Dimensions.PaddingMedium)
                    .align(AlignmentX.Center, AlignmentY.Center)
            }
            Text("Добавить предмет") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
        }
    }

    fun UiScope.ItemProperty(item: ItemStack) {
        Row(Grow.Std) {
            Image(icons.REMOVE) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(horizontal = Dimensions.PaddingMedium)
                    .alignY(AlignmentY.Center)
            }
            Row(Grow.Std) {
                modifier.padding(Dimensions.PaddingMedium)
                    .margin(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                    .border(
                        RoundRectBorder(
                            ColorTheme.UI.BackgroundAccent,
                            Dimensions.PaddingMedium,
                            Dimensions.PaddingSmall * 0.5f
                        )
                    )

                Item(item) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(Dimensions.PaddingNormal)
                    modifier.alignY(AlignmentY.Center)
                }

                Text(item.hoverName.string) {
                    modifier.alignY(AlignmentY.Center)
                    modifier.textColor(ColorTheme.UI.WhiteReplacement)
                }

                Box(Grow.Std) {}

                Arrow(ArrowScope.ROTATION_DOWN) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .colors(
                            ColorTheme.UI.BackgroundAccent,
                            ColorTheme.UI.WhiteReplacement
                        ).alignY(AlignmentY.Center)
                }
            }
            Row {
                modifier.padding(Dimensions.PaddingMedium)
                    .margin(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                    .border(
                        RoundRectBorder(
                            ColorTheme.UI.BackgroundAccent,
                            Dimensions.PaddingMedium,
                            Dimensions.PaddingSmall * 0.5f
                        )
                    )

                Text("${item.count * 10}") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                Text("%") {
                    modifier.textColor(ColorTheme.UI.BackgroundAccent).margin(horizontal = Dimensions.PaddingMedium)
                }
            }
            Text("до") {
                modifier.textColor(ColorTheme.UI.BackgroundAccent)
                    .alignY(AlignmentY.Center)
            }
            Row {
                modifier.padding(Dimensions.PaddingMedium)
                    .margin(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                    .border(
                        RoundRectBorder(
                            ColorTheme.UI.BackgroundAccent,
                            Dimensions.PaddingMedium,
                            Dimensions.PaddingSmall * 0.5f
                        )
                    )

                Text("${item.count}") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
            }
        }
    }
}