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
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
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
class TreeNode(val treeName: String, val treePath: String) : Composable {
    var isFolder = true
    var depth = 0
    val children: MutableList<TreeNode> = ArrayList()

    @Transient
    val isExpanded = mutableStateOf(false)

    fun walk(): MutableList<TreeNode> {
        val list = mutableListOf(this)
        if (isExpanded.value) list.addAll(children.flatMap { it.walk() })
        return list
    }

    fun toggleExpanded() {
        if (isFolder) {
            isExpanded.set(!isExpanded.value)
        }
    }

    fun sort() {
        children.sortBy { it.treeName }
        children.sortByDescending { it.isFolder }
        children.forEach { it.sort() }
    }

    companion object {
        val EMPTY = TreeNode("", "")
    }

    override fun UiScope.compose() {

        modifier.margin(sizes.smallGap)

        LazyList(
            containerModifier = { it.backgroundColor(null) },
            vScrollbarModifier = { it.width(10.dp).margin(5.dp) }
        ) {
            val editFilePopup by remember { mutableStateOf(EditPopup("Введите название файла: ", false)) }
            editFilePopup()
            val editFolderPopup by remember { mutableStateOf(EditPopup("Введите название папки: ", false)) }
            editFolderPopup()
            val deleteFilePopup by remember { mutableStateOf(WarningModalPopup("Вы действительно хотите удалить этот файл?")) }
            deleteFilePopup()
            val renamePopup by remember { mutableStateOf(EditPopup("Введите название нового файла: ", true)) }
            renamePopup()

            val itemPopupMenu = remember { ItemPopupMenu<TreeNode?>("scene-item-popup") }
            itemPopupMenu()
            var hoveredIndex by remember(-1)

            itemsIndexed(walk()) { i, item ->
                sceneObjectItem(item, i == hoveredIndex).apply {
                    modifier.onEnter { hoveredIndex = i }.onExit { hoveredIndex = -1 }
                        .onClick {
                            if (it.pointer.isRightButtonClicked) {
                                itemPopupMenu.hide()
                                itemPopupMenu.show(it.screenPosition, makeMenu(item, editFilePopup, editFolderPopup, renamePopup, deleteFilePopup), item)
                            }
                        }
                }
            }
        }

    }

    private fun UiScope.sceneObjectItem(item: TreeNode, isHovered: Boolean) {
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                    if (item.isFolder) {
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

    private fun UiScope.sceneObjectLabel(item: TreeNode, isHovered: Boolean) =
        Row(width = Grow.Std) {
            // tree-depth based indentation
            if (item.depth > 0) {
                Box(width = 35.dp * item.depth) {}
            }

            // expand / collapse arrow
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
}

val CLIENT_RESOURCES =
    listOf("blockstates", "font", "lang", "shaders", "textures", "particles", "models", "animations", "geo")
val SERVER_RESOURCES = listOf("recipe", "advancement", "loot_table", "tags", "worldgen")
val PROJECT_FOLDERS = listOf("assets", "data", "npcs", "replays", "scripts")
val PROJECT_FILE_TYPES = listOf(
    "kts", "json", "json", "nbt", "mcfunction",
    "png", "jpg",
    "gltf", "glb",
    "fsh", "vsh", "mp3"
)

@HollowPacketV2
@Serializable
class RequestTreePacket(var tree: TreeNode = TreeNode.EMPTY) : RequestPacket<RequestTreePacket>() {
    override fun retrieveValue(player: ServerPlayer) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("You don't have permissions to open scripts!".literal)
            return
        }

        tree = projectTree(DirectoryManager.HOLLOW_ENGINE.toFile()) {
            val folder = it.toReadablePath().substringBefore("/")
            val extension = it.name.substringAfterLast(".")
            val isFolder = !it.isFile

            folder in PROJECT_FOLDERS && (isFolder || extension in PROJECT_FILE_TYPES)
        }
    }

    private fun projectTree(file: File, depth: Int = 0, predicate: (File) -> Boolean): TreeNode {
        val tree = TreeNode(file.name, file.toReadablePath())
        tree.depth = depth
        tree.isFolder = file.isDirectory
        file.listFiles()
            ?.filter { predicate(it) }
            ?.forEach { tree.children.add(projectTree(it, depth + 1, predicate)) }
        tree.sort()
        return tree
    }
}