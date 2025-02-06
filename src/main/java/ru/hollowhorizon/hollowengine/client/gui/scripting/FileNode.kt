package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hc.client.kool.MCGlApi
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.RequestPacket
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.kool.lineHeight
import ru.hollowhorizon.hollowengine.client.kool.DndHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

fun createTexture(location: ResourceLocation, width: Int, height: Int) = Texture2d().apply {
    gpuTexture =
        LoadedTextureGl(MCGlApi.TEXTURE_2D, GlTexture(location.toTexture().id), MCGlApi.backend, this, 0L).apply {
            this.width = width
            this.height = height
        }
    loadingState = Texture.LoadingState.LOADED
}

val FOLDER by lazy { createTexture("hollowengine:textures/gui/icons/folder.png".rl, 16, 16) }
val FOLDER_OPEN by lazy { createTexture("hollowengine:textures/gui/icons/folder_open.png".rl, 16, 16) }
val FILE by lazy { createTexture("hollowengine:textures/gui/icons/file.png".rl, 16, 16) }
val KOTLIN by lazy { createTexture("hollowengine:textures/gui/icons/file_kts.png".rl, 16, 16) }
val CLOSE by lazy { createTexture("hollowengine:textures/gui/icons/close.png".rl, 16, 16) }

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
        LazyList(
            containerModifier = { it.backgroundColor(null) },
            vScrollbarModifier = { it.width(10.dp).margin(5.dp) }
        ) {
            val filePopup = remember(::FilePopup)
            filePopup()
            var hoveredIndex by remember(-1)

            itemsIndexed(walk()) { i, item ->
                sceneObjectItem(item, i == hoveredIndex).apply {
                    modifier.onEnter { hoveredIndex = i }.onExit { hoveredIndex = -1 }
                        .onClick {
                            if (it.pointer.isRightButtonClicked) {
                                filePopup.show(item, it.screenPosition)
                            }
                        }
                }
            }
        }

    }

    protected open fun UiScope.sceneObjectItem(item: FileNode, isHovered: Boolean) {
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                    if (item.isFolder) {
                        item.toggleExpanded()
                    } else {
                        val file = IDEGuiV2.files[item.treePath]

                        if (file == null) RequestFilePacket(item.treePath).send()
                        else IDEGuiV2.dock.getLeafAtPath("0/1")?.bringToTop(file.dockable)
                    }
                }
            }
            .margin(horizontal = sizes.smallGap)
            .padding(horizontal = sizes.smallGap)

        if (isHovered) {
            modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
        }

        //sceneObjectDndHandler(item)
        sceneObjectLabel(item, isHovered)
    }

    protected fun UiScope.sceneObjectDndHandler(item: FileNode) {
        val dndHandler = rememberItemDndHandler(item)

        if (dndHandler.isHovered.use()) {
            modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
        }

        modifier.installDragAndDropHandler(IDEGuiV2.dndContext, dndHandler) { item }
    }

    private fun UiScope.rememberItemDndHandler(treeItem: FileNode): FileHandler {
        val handler = remember { FileHandler(treeItem, uiNode) }
        IDEGuiV2.dndContext.registerHandler(handler)
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
            success: Boolean
        ) {
            super.onDragEnd(dragItem, dragPointer, source, target, success)
            (target as? FileHandler)?.node?.let { node ->
                if(!node.isFolder || dragItem.treePath == node.treePath) return@let
                CopyFilePacket(dragItem.treePath, node.treePath, true).send()
            }
        }
    }

    protected open fun UiScope.sceneObjectLabel(item: FileNode, isHovered: Boolean) =
        Row(width = Grow.Std) {
            if (item.depth > 0) {
                Box(width = 35.dp * item.depth) {}
            }

            Box {
                modifier
                    .size(sizes.lineHeight * 0.8f, sizes.lineHeight)
                    .alignY(AlignmentY.Center)
                if (item.isFolder) {
                    Arrow(isHoverable = false) {
                        modifier
                            .rotation(if (item.isExpanded.use()) ROTATION_DOWN else ROTATION_RIGHT)
                            .align(AlignmentX.Center, AlignmentY.Center)
                            .onClick { item.toggleExpanded() }
                            .size(25.dp, 25.dp)
                    }
                }
            }

            val fgColor = if (isHovered) colors.primary else colors.secondary

            val icon = when {
                item.isFolder && item.isExpanded.value -> FOLDER_OPEN
                item.isFolder && !item.isExpanded.value -> FOLDER
                item.treeName.endsWith(".kts") -> KOTLIN
                else -> FILE
            }

            Box {
                modifier.alignY(AlignmentY.Center)
                Image(icon) {
                    modifier.margin(horizontal = 10.dp).size(sizes.lineHeight, sizes.lineHeight)
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

@HollowPacketV2
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