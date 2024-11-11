package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.RequestPacket
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import java.io.File

@Serializable
class TreeNode(val treeName: String, val treePath: String) {
    var isFolder = true
    val children: MutableList<TreeNode> = ArrayList()

    fun sort() {
        children.sortBy { it.treeName }
        children.sortByDescending { it.isFolder }
        children.forEach { it.sort() }
    }

    companion object {
        val EMPTY = TreeNode("", "")
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
            player.sendSystemMessage("You don't have permissions to open scripts!".mcText)
            return
        }

        tree = TreeNode("Проект", "").apply {
            children += projectTree(DirectoryManager.HOLLOW_ENGINE.toFile()) {
                val folder = it.toReadablePath().substringBefore("/")
                val extension = it.name.substringAfterLast(".")
                val isFolder = !it.isFile

                folder in PROJECT_FOLDERS && (isFolder || extension in PROJECT_FILE_TYPES)
            }
        }
    }

    private fun projectTree(file: File, predicate: (File) -> Boolean): TreeNode {
        val tree = TreeNode(file.name, file.toReadablePath())
        tree.isFolder = file.isDirectory
        file.listFiles()
            ?.filter { predicate(it) }
            ?.forEach { tree.children.add(projectTree(it, predicate)) }
        tree.sort()
        return tree
    }
}