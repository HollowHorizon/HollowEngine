package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IconHelper
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.gui.tree.TreeBackgroundRenderer
import ru.hollowhorizon.hollowengine.client.kool.DndHandler
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

@Serializable
open class FileNode(val treeName: String, val treePath: String, var depth: Int = 0) {
    var isFolder = false
    val children: MutableList<FileNode> = ArrayList()

    @Transient
    val isExpanded = mutableStateOf(false)

    @Transient
    var parent: FileNode? = null

    init {
        update()
    }

    fun walk(filter: String): MutableList<FileNode> {
        val list = mutableListOf(this)
        if (isExpanded.value) list.addAll(
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

        // Открываем / Закрываем папку
        isExpanded.set(!isExpanded.value)
        if (isExpanded.value) update()
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
                if (old[treeName] == true) toggleExpanded()
            }
        })
        sort()
    }

    private fun sort() {
        children.sortBy { it.treeName }
        children.sortBy { !it.isFolder }
        children.forEach { it.sort() }
    }

    fun UiScope.draw(filter: String) {
        val filePopup = remember(::FilePopup)

        LazyColumn(
            containerModifier = { it.backgroundColor(null) },
            vScrollbarModifier = {
                it.width(sizes.smallGap).colors(
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
        sceneObjectLabel(item)
    }

    protected fun UiScope.sceneObjectDndHandler(item: FileNode) {
        val dndHandler = rememberItemDndHandler(item)

        if (dndHandler.isHovered.use()) {
            modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, sizes.gap))
        }

        modifier.installDragAndDropHandler(IdeContent.dndContext, dndHandler) { item }
    }

    private fun UiScope.rememberItemDndHandler(treeItem: FileNode): FileHandler {
        val handler = remember { FileHandler(treeItem, uiNode) }
        IdeContent.dndContext.registerHandler(handler)
        return handler
    }

    private class FileHandler(val node: FileNode, uiNode: UiNode) : DndHandler(uiNode) {
        val insertPos = mutableStateOf(0)

        override fun onMatchingHover(
            dragItem: FileNode,
            dragPointer: PointerEvent,
            source: DragAndDropHandler<FileNode>?,
            isHovered: Boolean,
        ) {
            super.onMatchingHover(dragItem, dragPointer, source, isHovered)

            val h = dropTarget.heightPx
            val hoverPtrPos = dropTarget.toLocal(dragPointer.screenPosition)

            when {
                hoverPtrPos.y < h * 0.25f -> insertPos.set(-1)
                hoverPtrPos.y > h * 0.75f -> insertPos.set(1)
                else -> insertPos.set(0)
            }
        }

        override fun onDragEnd(
            dragItem: FileNode,
            dragPointer: PointerEvent,
            source: DragAndDropHandler<FileNode>?,
            target: DragAndDropHandler<FileNode>?,
            success: Boolean,
        ) {
            super.onDragEnd(dragItem, dragPointer, source, target, success)
            (target as? FileHandler)?.node?.let { node ->
                if (!node.isFolder || dragItem.treePath == node.treePath) return@let
                //CopyFilePacket(dragItem.treePath, node.treePath, true).send()
            }
        }
    }

    protected open fun UiScope.sceneObjectLabel(item: FileNode) =
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
                                else ScriptingEnvironmentOverlay.dock.getLeafAtPath("0/1")?.bringToTop(file.dockable)
                            }
                        }
                    }

                val isHovered by modifier.hoverable()
                val bgColor by animateColorAsState(
                    if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
                    tween(easing = Easing.quadRev)
                )
                val fgColor by animateColorAsState(
                    if (isHovered) Color("C4CBDAFF") else Color("9099ACFF"),
                    tween(easing = Easing.quadRev)
                )

                modifier.background(RoundRectBackground(bgColor, sizes.smallGap))
                    .padding(Dimensions.PaddingNormal)

                Box {
                    if (item.depth != 0) modifier.margin(start = Dimensions.PaddingMedium)
                    modifier.alignY(AlignmentY.Center)
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
                    if (fadingOutIcon != null) {
                        Image(fadingOutIcon.toString()) {
                            modifier
                                .size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                                .imageSize(ImageSize.Stretch)
                                .tint(Color.WHITE.withAlpha(1f - crossfadeAnim.use()))
                        }
                    }
                    Image(activeIcon) {
                        modifier.size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                            .imageSize(ImageSize.Stretch)
                            .tint(Color.WHITE.withAlpha(crossfadeAnim.use()))

                    }
                }

                Box(width = Grow.Std, height = Grow.Std) {
                    modifier.margin(start = Dimensions.PaddingMedium)
                    Text(item.treeName) {
                        modifier
                            .alignY(AlignmentY.Center)
                            .textColor(fgColor)
                    }
                }
            }
        }

    companion object {
        val EMPTY = FileNode("HollowEngine", "").apply { isFolder = true }
    }
}