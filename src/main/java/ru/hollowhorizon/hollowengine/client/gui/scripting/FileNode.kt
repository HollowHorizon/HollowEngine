package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.network.RequestPacket
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IconHelper
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener
import ru.hollowhorizon.hollowengine.client.kool.DndHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

@Serializable
open class FileNode(val treeName: String, val treePath: String) : Composable {
    var isFolder = false
    var depth = 0
    val children: MutableList<FileNode> = ArrayList()

    @Transient
    val isExpanded = mutableStateOf(false)

    @Transient
    var parent: FileNode? = null

    fun walk(): MutableList<FileNode> {
        val list = mutableListOf(this)
        if (isExpanded.value) list.addAll(children.flatMap { it.walk() })
        return list
    }

    open fun toggleExpanded() {
        if (!isFolder) return

        // При закрытии папки удаляем из памяти её содержимое, чтобы при открытии оно обновилось
        if (isExpanded.value) this.children.clear()
        else {
            // Запрашиваем у сервера содержимое папки (оно придёт с задержкой)
            scopeSync {
                val result = RequestFolderPacket(this@FileNode.treePath).request()

                // Добавляем новые элементы *перед* отрисовкой
                // Чтобы не получить ConcurrentModificationException
                launchOnMainThread {
                    this@FileNode.children.addAll(result.children.map { child ->
                        FileNode(
                            child.name,
                            if (this@FileNode.treePath.isEmpty()) child.name else this@FileNode.treePath + "/" + child.name
                        ).apply {
                            parent = this@FileNode
                            depth = this@FileNode.depth + 1
                            isFolder = child.isFolder
                        }
                    })
                    this@FileNode.sort()
                }
            }
        }

        // Открываем / Закрываем папку
        isExpanded.set(!isExpanded.value)
    }

    private fun sort() {
        children.sortBy { it.treeName }
        children.sortBy { !it.isFolder }
        children.forEach { it.sort() }
    }

    override fun UiScope.compose() {
        modifier.margin(sizes.smallGap)
        val filePopup = remember(::FilePopup)

        LazyColumn(
            containerModifier = { it.backgroundColor(null) },
            vScrollbarModifier = { it.width(sizes.smallGap) },
            withHorizontalScrollbar = true
        ) {

            filePopup()

            itemsIndexed(walk()) { i, item ->
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
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                    if (item.isFolder) {
                        item.toggleExpanded()
                    } else {
                        val screen = Minecraft.getInstance().screen as? ScriptingEnvironmentScreen ?: return@onClick
                        val file = IdeContent.files[item.treePath]

                        if (file == null) RequestFilePacket(item.treePath).send()
                        else screen.dock.getLeafAtPath("0/1")?.bringToTop(file.dockable)
                    }
                }
            }

        val (bgColor, fgColor) = hoverColors(0.5f,
            listOf(colors.background, Color("9099ACFF")),
            listOf(IdeTheme.hoveredColors.background, Color("C4CBDAFF"))
        )

        modifier.background(RoundRectBackground(bgColor, sizes.smallGap))
        sceneObjectLabel(item, fgColor)
    }

    protected fun UiScope.sceneObjectDndHandler(item: FileNode) {
        val dndHandler = rememberItemDndHandler(item)

        if (dndHandler.isHovered.use()) {
            modifier.background(RoundRectBackground(IdeTheme.hoveredColors.background, sizes.gap))
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
                CopyFilePacket(dragItem.treePath, node.treePath, true).send()
            }
        }
    }

    protected open fun UiScope.sceneObjectLabel(item: FileNode, fgColor: Color) =
        Row(width = Grow.Std) {
            modifier.padding(start = sizes.smallGap)
            if (item.depth > 0) {
                Box(width = (14.dp + sizes.smallGap * 2) * item.depth + if (!item.isFolder) 14.dp + sizes.smallGap * 2 else 0.dp) {}
            }

            Box {
                modifier.alignY(AlignmentY.Center)
                    .padding(sizes.smallGap)

                if (item.isFolder) {
                    Arrow(isHoverable = false) {
                        modifier
                            .rotation(if (item.isExpanded.use()) ROTATION_DOWN else ROTATION_RIGHT)
                            .align(AlignmentX.Center, AlignmentY.Center)
                            .onClick { item.toggleExpanded() }
                            .size(14.dp, 14.dp)
                    }
                }
            }

            val icon = IconHelper.forPath(item.treePath, item.isFolder, item.isExpanded.use())

            Box {
                modifier.alignY(AlignmentY.Center)
                Image(icon) {
                    modifier.margin(horizontal = sizes.smallGap).size(24.dp, 24.dp)
                        .imageSize(ImageSize.Stretch)
                }
            }

            Box(width = Grow.Std, height = Grow.Std) {
                Text(item.treeName) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .textColor(fgColor)
                }
            }
        }

    companion object {
        val EMPTY = FileNode("HollowEngine", "").apply { isFolder = true }
    }
}

@HollowPacketHandler
@Serializable
class RequestFolderPacket(private var folder: String) : RequestPacket<RequestFolderPacket>() {
    val children = mutableListOf<Child>()

    override fun retrieveValue(player: ServerPlayer) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to open scripts!".literal)
            return
        }

        val file = folder.fromReadablePath()

        file.listFiles()?.forEach {
            children.add(Child(it.name, it.isDirectory))
        }
    }

    @Serializable
    class Child(val name: String, val isFolder: Boolean)
}