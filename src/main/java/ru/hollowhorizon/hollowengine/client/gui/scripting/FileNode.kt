package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hc.client.kool.MCGlApi
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.RequestPacket
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.kool.lineHeight
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import java.io.File

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
class FileNode(val treeName: String, val treePath: String) : Composable {
    val isFolder get() = children.isNotEmpty()
    var depth = 0
    val children: MutableList<FileNode> = ArrayList()

    @Transient
    val isExpanded = mutableStateOf(false)

    fun walk(): MutableList<FileNode> {
        val list = mutableListOf(this)
        if (isExpanded.value) list.addAll(children.flatMap { it.walk() })
        return list
    }

    fun toggleExpanded() {
        if (children.isNotEmpty()) {
            isExpanded.set(!isExpanded.value)
        }
    }

    fun sort() {
        children.sortBy { it.treeName }
        children.sortBy { it.children.isEmpty() }
        children.forEach { it.sort() }
    }

    override fun UiScope.compose() {

        modifier.margin(sizes.smallGap)

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

    private fun UiScope.sceneObjectItem(item: FileNode, isHovered: Boolean) {
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                    if (item.children.isNotEmpty()) {
                        item.toggleExpanded()
                    } else {
                        val file = IDEGuiV2.files.find { it.filePath == item.treePath }

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

        sceneObjectLabel(item, isHovered)
    }

    private fun UiScope.sceneObjectLabel(item: FileNode, isHovered: Boolean) =
        Row(width = Grow.Std) {
            if (item.depth > 0) {
                Box(width = 35.dp * item.depth) {}
            }

            Box {
                modifier
                    .size(sizes.lineHeight * 0.8f, sizes.lineHeight)
                    .alignY(AlignmentY.Center)
                if (item.children.isNotEmpty()) {
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
                item.children.isNotEmpty() && item.isExpanded.value -> FOLDER_OPEN
                item.children.isNotEmpty() && !item.isExpanded.value -> FOLDER
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
        val EMPTY = FileNode("", "")
    }
}

@HollowPacketV2
@Serializable
class RequestTreePacket(var tree: FileNode = FileNode.EMPTY) : RequestPacket<RequestTreePacket>() {
    override fun retrieveValue(player: ServerPlayer) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to open scripts!".literal)
            return
        }

        tree = projectTree(DirectoryManager.HOLLOW_ENGINE.toFile())
    }

    private fun projectTree(file: File, depth: Int = 0, predicate: (File) -> Boolean = { true }): FileNode {
        val tree = FileNode(file.name, file.toReadablePath())
        tree.depth = depth
        file.listFiles()
            ?.filter { predicate(it) }
            ?.forEach { tree.children += (projectTree(it, depth + 1, predicate)) }
        tree.sort()
        return tree
    }
}