package ru.hollowhorizon.hollowengine.client.gui.scripting.popup

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.audio.UIAudio
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.kool.menuDivider
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionColumnLayout
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.util.Node

class ItemPopupMenu<T>(scopeName: String, hideOnOutsideClick: Boolean = true) :
    AutoPopup(hideOnOutsideClick = hideOnOutsideClick, scopeName = scopeName) {

    private val menu = mutableStateOf<SubMenuItem<T>?>(null)
    private var contextItem = mutableStateOf<ContextItemHolder<T>?>(null)

    val item get() = contextItem.value?.item

    init {
        popupContent = Composable {
            val item = contextItem.use()
            val rootMenu = menu.use()
            if (item != null && rootMenu != null) {
                modifier
                    .layout(CellLayout)
                    .zLayer(100_000_000)
                    .backgroundColor(null)
                    .onClick { it.isConsumed = false }
                    .onHover { it.isConsumed = false }
                    .onEnter { it.isConsumed = false }
                    .onExit { it.isConsumed = false }
                    .onDrag { it.isConsumed = false }
                    .onDragStart { it.isConsumed = false }
                    .onDragEnd { it.isConsumed = false }

                menuList(rootMenu.menuItems.use(), item.item, Dp.ZERO, Dp.ZERO, modifier.zLayer)
            }
        }
    }

    fun show(screenPosPx: Vec2f, menu: SubMenuItem<T>, contextItem: T) {
        super.show(screenPosPx)
        this.menu.set(menu)
        this.contextItem.set(ContextItemHolder(contextItem))
    }

    fun updateMenu(menu: SubMenuItem<T>) {
        this.menu.set(menu)
    }

    override fun hide() {
        super.hide()
        contextItem.set(null)
    }

    private fun UiScope.menuList(items: List<ContextMenuItem<T>>, contextItem: T, x: Dp, y: Dp, z: Int) {
        var subMenu by remember<SubMenuItem<T>?>(null)
        var subMenuNode by remember<UiNode?>(null)
        val withIcons = items.any { (it is MenuItem && it.icon != null) || (it is SubMenuItem && it.icon != null) }

        menuColumn(x, y, z) { opacity ->
            items.forEach { item ->
                when (item) {
                    is MenuItem -> {
                        Row(width = Grow.Std) {
                            var isHovered by remember(false)
                            modifier
                                .onEnter {
                                    isHovered = true
                                    subMenu = null
                                    subMenuNode = null
                                }
                                .onExit { isHovered = false }
                                .onClick {
                                    UIAudio.SELECT.play()
                                    item.action.invoke(contextItem)
                                    if (item.closeOnClick) {
                                        hide()
                                    }
                                }
                                .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)

                            val color by animateColorAsState(if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary)
                            modifier.background(RoundRectBackground(color.withAlpha(opacity), Dimensions.PaddingMedium))


                            iconBox(withIcons, item.icon, Color.WHITE.withAlpha(opacity))
                            Text(item.label.lang) {
                                modifier
                                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(opacity))
                                    .alignY(AlignmentY.Center)
                            }
                        }
                    }

                    is SubMenuItem -> {
                        Row(width = Grow.Std) {
                            var isHovered by remember(false)
                            modifier
                                .onEnter {
                                    isHovered = true
                                    subMenu = item
                                    subMenuNode = uiNode
                                }
                                .onExit { isHovered = false }

                            val color by animateColorAsState(if (isHovered || subMenu == item) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary)

                            modifier.background(RoundRectBackground(color.withAlpha(opacity), Dimensions.PaddingMedium))


                            item.icon?.let { iconBox(withIcons, it, (item.color ?: Color.WHITE).withAlpha(opacity)) }
                            Text(item.label?.lang ?: "Sub menu") {
                                modifier.alignY(AlignmentY.Center)
                                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(opacity))
                            }
                            Box(Grow.Std) { }
                            Arrow {
                                modifier.alignY(AlignmentY.Center)
                                    .margin(start=Dimensions.PaddingMedium)
                                    .colors(
                                        ColorTheme.UI.BackgroundAccent.withAlpha(opacity),
                                        ColorTheme.UI.WhiteReplacement.withAlpha(opacity)
                                    )
                                    .size(Dimensions.PaddingMedium, Dimensions.PaddingMedium)
                            }
                        }
                    }

                    is Divider -> {
                        menuDivider(
                            marginStart = Dimensions.PaddingMedium,
                            marginEnd = Dimensions.PaddingMedium,
                            marginTop = Dimensions.PaddingSmall,
                            marginBottom = Dimensions.PaddingSmall,
                            color = ColorTheme.UI.BackgroundElements.withAlpha(opacity)
                        )
                    }
                }
            }
        }

        val subMenuItems = subMenu?.menuItems?.use()
        val anchorNode = subMenuNode
        if (!subMenuItems.isNullOrEmpty() && anchorNode != null) {
            val subPos = uiNode.toLocal(anchorNode.rightPx, anchorNode.topPx)
            menuList(
                subMenuItems,
                contextItem,
                Dp.fromPx(subPos.x) - sizes.smallGap,
                Dp.fromPx(subPos.y) - sizes.smallGap,
                z + UiSurface.LAYER_POPUP
            )
        }
    }

    private fun UiScope.iconBox(withIcons: Boolean, icon: ResourceLocation?, color: Color) {
        if (withIcons) {
            if (icon != null) {
                Image {
                    modifier
                        .margin(Dimensions.PaddingNormal)
                        .alignY(AlignmentY.Center)
                        .iconImage(remember {
                            ImageManager.load(icon, SamplerMode.NEAREST)
                        }, Dimensions.PaddingHuge, color)
                }
            } else {
                Box(Dimensions.PaddingHuge, Dimensions.PaddingHuge) { modifier.margin(Dimensions.PaddingNormal) }
            }
        }
    }

    private inline fun UiScope.menuColumn(
        x: Dp,
        y: Dp,
        z: Int,
        crossinline block: ColumnScope.(opacity: Float) -> Unit,
    ) {
        ScrollArea(containerModifier = {
            it.height(Grow(1f, max = 500.dp))
                .background(null)
        }, vScrollbarModifier = {
            it.colors(
                trackColor = ColorTheme.UI.BackgroundSecondary,
                trackHoverColor = ColorTheme.UI.BackgroundElements,
                color = ColorTheme.UI.BackgroundAccent,
                hoverColor = ColorTheme.UI.WhiteReplacement
            )
        }) {

            val opacity = rememberAnimatableFloat(0f)

            LaunchedEffect(Unit) {
                opacity.animateTo(1f, 0.3f, Easing.easeOutQuart)
            }

            modifier.margin(start = x, top = y)
                .zLayer(z + 500)
                .padding(Dimensions.PaddingMedium)
                .background(
                    RoundRectBackground(
                        ColorTheme.UI.BackgroundSecondary.withAlpha(opacity.use()),
                        Dimensions.PaddingMedium
                    )
                )
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundElements.withAlpha(opacity.use()),
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall
                    )
                )


            Column(Grow.Std) {
                modifier.layout(AccordionColumnLayout(opacity.use()))

                block(opacity.use())
            }
        }
    }

    class ContextItemHolder<T>(val item: T)
}

sealed class ContextMenuItem<T : Any?>

class Divider<T : Any?> : ContextMenuItem<T>()

class MenuItem<T : Any?>(
    val label: String,
    val icon: ResourceLocation?,
    val closeOnClick: Boolean,
    val action: ((T) -> Unit),
) : ContextMenuItem<T>()

class SubMenuItem<T : Any?>(val label: String?, val icon: ResourceLocation?, val color: Color?) : ContextMenuItem<T>() {
    val menuItems: MutableStateList<ContextMenuItem<T>> = mutableStateListOf()

    fun item(
        label: String,
        icon: ResourceLocation? = null,
        closeOnClick: Boolean = true,
        action: (T) -> Unit,
    ) {
        menuItems += MenuItem(label, icon, closeOnClick, action)
    }

    fun subMenu(label: String, icon: ResourceLocation? = null, color: Color? = null, block: SubMenuItem<T>.() -> Unit) {
        val subMenu = SubMenuItem<T>(label, icon, color)
        subMenu.block()
        menuItems += subMenu
    }

    fun divider() {
        menuItems += Divider()
    }
}

fun <T : Any?> SubMenuItem(
    label: String? = null,
    icon: ResourceLocation? = null,
    color: Color? = null,
    block: SubMenuItem<T>.() -> Unit,
): SubMenuItem<T> {
    val menu = SubMenuItem<T>(label, icon, color)
    menu.block()
    return menu
}

fun SubMenuItem<Node>.loadMenu(node: Node, action: (Node) -> Unit) {
    if (node.children.isNotEmpty()) {
        subMenu(node.name) {
            node.children.values.forEach {
                loadMenu(it, action)
            }
        }
    } else {
        item(node.name) { action(node) }
    }
}

fun ImageModifier.iconImage(iconProvider: Texture2d, size: Dimension, tintColor: Color? = null): ImageModifier {
    size(size, size)
    imageProvider(FlatImageProvider(iconProvider))
    tintColor?.let { tint(it) }
    return this
}
