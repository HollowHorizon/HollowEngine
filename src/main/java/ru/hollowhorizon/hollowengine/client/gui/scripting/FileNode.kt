package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.FrontendScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IconHelper
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.gui.tree.TreeBackgroundRenderer
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

@Serializable
open class FileNode(val treeName: String, val treePath: String, var depth: Int = 0) {
    var isFolder = false
    val children: MutableList<FileNode> = ArrayList()

    @Transient
    val isExpanded = mutableStateOf(false)

    @Transient
    val expandAnim = AnimatableFloat(0f)

    @Transient
    var parent: FileNode? = null

    init {
        update()
    }

    fun walk(filter: String): MutableList<FileNode> {
        val list = mutableListOf(this)
        if (isExpanded.value || expandAnim.value > 0f) list.addAll(
            children
                .filter { it.canShow(filter) }
                .flatMap { it.walk(filter) }
        )
        return list
    }

    fun canShow(filter: String): Boolean {
        if (treePath.contains(filter, ignoreCase = true)) return true
        if (children.any { it.canShow(filter) }) return true
        return false
    }

    open fun toggleExpanded() {
        if (!isFolder) return

        val targetState = !isExpanded.value
        isExpanded.set(targetState)

        FrontendScope.launch {
            expandAnim.animateTo(
                targetValue = if (targetState) 1f else 0f,
                duration = 0.3f,
                easing = Easing.easeOutQuart
            )
        }

        if (targetState) update()
    }

    fun update() {
        children.clear()
        val result = treePath.fromReadablePath().listFiles()?.toList() ?: emptyList()

        val old = children.associate { it.treeName to it.isExpanded.value }
        children.addAll(result.map { child ->
            FileNode(
                child.name,
                if (this@FileNode.treePath.isEmpty()) child.name
                else this@FileNode.treePath + "/" + child.name,
                depth + 1
            ).apply {
                parent = this@FileNode
                isFolder = !child.isFile
                if (old[treeName] == true) {
                    isExpanded.set(true)
                    expandAnim.set(1f)
                    update()
                }
            }
        })
        sort()
    }

    private fun sort() {
        children.sortBy { it.treeName }
        children.sortBy { !it.isFolder }
        children.forEach { it.sort() }
    }

    private fun UiScope.calculateVisibility(): Float {
        var visibility = 1f
        var current = this@FileNode.parent
        while (current != null) {
            visibility *= current.expandAnim.use()
            if (visibility == 0f) return 0f
            current = current.parent
        }
        return visibility
    }

    fun UiScope.draw(filter: String) {
        val filePopup = remember(::FilePopup)

        LazyColumn(
            containerModifier = { it.backgroundColor(null) },
            scrollPaneModifier = { it.width(FitContent).margin(horizontal = Dimensions.PaddingNormal) },
            vScrollbarModifier = {
                it.width(Dimensions.PaddingMedium).colors(
                    ColorTheme.UI.BackgroundElements,
                    ColorTheme.UI.BackgroundAccent,
                    Color.WHITE.withAlpha(0f),
                    ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                )
            },
            hScrollbarModifier = {
                it.height(Dimensions.PaddingMedium).colors(
                    ColorTheme.UI.BackgroundElements,
                    ColorTheme.UI.BackgroundAccent,
                    Color.WHITE.withAlpha(0f),
                    ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                )
            },
            withHorizontalScrollbar = true
        ) {

            filePopup()

            itemsIndexed(walk(filter)) { i, item ->
                sceneObjectItem(item)

                modifier.onClick {
                    if (it.pointer.isRightButtonClicked) {
                        filePopup.show(item, it.screenPosition)
                    }
                }
            }
        }

    }

    protected open fun UiScope.sceneObjectItem(item: FileNode) {
        val visibility = with(item) { calculateVisibility() }

        Box(width = Grow.Std) {
            if (visibility < 1f) {
                modifier.layout(AccordionLayout(visibility))
            }

            Row(width = Grow.Std) {


                modifier.padding(start = sizes.smallGap)

                Row(height = Grow.Std) {
                    for (i in 0 until item.depth) {
                        Box(Grow.Std) {
                            modifier
                                .align(AlignmentX.Center, AlignmentY.Center)
                                .onClick { item.toggleExpanded() }
                                .size(Dimensions.PaddingLarge, Grow.Std)
                                .background(TreeBackgroundRenderer(item.isExpanded.use() && i == item.depth - 1))

                            if (i != 0) modifier.margin(start = Dimensions.PaddingMedium)
                        }
                    }
                }

                val icon = IconHelper.forPath(item.treePath, item.isFolder, item.isExpanded.use())

                Row(width = Grow.Std) {
                    modifier
                        .onClick { evt ->
                            if (evt.pointer.isLeftButtonClicked) {
                                if (item.isFolder) {
                                    item.toggleExpanded()
                                } else {
                                    val file = IdeContent.files[item.treePath]

                                    if (file == null) IdeContent.openFile(
                                        item.treePath,
                                        item.treePath.fromReadablePath().readBytes()
                                    )
                                    else ScriptingEnvironmentOverlay.dock.getLeafAtPath("0/1")
                                        ?.bringToTop(file.dockable)
                                }
                            }
                        }

                    val isHovered by modifier.hoverable()
                    val bgColor by animateColorAsState(
                        if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
                        tween(easing = Easing.easeOutQuart)
                    )
                    val fgColor by animateColorAsState(
                        if (isHovered) Color("C4CBDAFF") else Color("9099ACFF"),
                        tween(easing = Easing.easeOutQuart)
                    )

                    modifier.background(RoundRectBackground(bgColor, sizes.smallGap))
                        .padding(Dimensions.PaddingNormal)

                    Box(scopeName = item.treePath) {
                        if (item.depth != 0) modifier.margin(start = Dimensions.PaddingMedium)
                        modifier.align(AlignmentX.Center, AlignmentY.Center)
                            .size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                        val crossfadeAnim = remember { AnimatableFloat(1f) }
                        var activeIcon by remember { mutableStateOf(icon) }
                        var fadingOutIcon by remember { mutableStateOf<ResourceLocation?>(null) } // Тип Any? или тип, который возвращает IconHelper

                        LaunchedEffect(icon) {
                            if (icon != activeIcon) {
                                fadingOutIcon = activeIcon
                                activeIcon = icon
                                crossfadeAnim.set(0f)
                                crossfadeAnim.animateTo(
                                    targetValue = 1f,
                                    duration = 0.3f,
                                    easing = Easing.easeOutQuart
                                )
                                fadingOutIcon = null
                            }
                        }
                        val iconSize = Dimensions.PaddingHuge + Dimensions.PaddingNormal
                        if (fadingOutIcon != null) {
                            Image(fadingOutIcon.toString()) {
                                modifier
                                    .size(iconSize, iconSize)
                                    .imageSize(ImageSize.Stretch)
                                    .align(AlignmentX.Center, AlignmentY.Center)
                                    .tint(Color.WHITE.withAlpha(1f - crossfadeAnim.use()))
                            }
                        }
                        Image(activeIcon) {
                            modifier.size(iconSize, iconSize)
                                .imageSize(ImageSize.Stretch)
                                .align(AlignmentX.Center, AlignmentY.Center)
                                .tint(Color.WHITE.withAlpha(crossfadeAnim.use()))

                        }
                    }

                    Box {
                        modifier.margin(Dimensions.PaddingMedium, Dimensions.PaddingHuge)
                            .alignY(AlignmentY.Center)
                        Text(item.treeName) {
                            modifier
                                .alignY(AlignmentY.Center)
                                .textColor(fgColor)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val EMPTY = FileNode("HollowEngine", "").apply { isFolder = true }
    }
}

class AccordionLayout(val expansion: Float) : Layout {
    override fun measureContentSize(uiNode: UiNode, ctx: KoolContext) {
        ColumnLayout.measureContentSize(uiNode, ctx)
        uiNode.setContentSize(uiNode.contentWidthPx, uiNode.contentHeightPx * expansion)
    }

    override fun layoutChildren(uiNode: UiNode, ctx: KoolContext) {
        ColumnLayout.layoutChildren(uiNode, ctx)
    }
}