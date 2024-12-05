package ru.hollowhorizon.hollowengine.client.gui.scripting


import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.rl

fun TreeNode.draw(
    drawFolderPopup: (popup: String, path: String) -> Unit,
    drawFilePopup: (popup: String, path: String) -> Unit,
) {

    val popupName = (if (isFolder) "Folder" else "File") + "Popup##$treePath"

    if (isFolder) drawFolderPopup(popupName, treePath)
    else drawFilePopup(popupName, treePath)

    var hovered = false
    var ignore = false

    drawIcon(isFolder, treeName)

}

private fun drawIcon(isFolder: Boolean, treeName: String) {
    val folders = mapOf(
        "camera" to "folder_camera",
        "npcs" to "folder_npcs",
        "replays" to "folder_replays",
        "scripts" to "folder_scripts",
        "storyteller_dimension" to "folder_world"
    )
    val file = if (isFolder) {
        folders[treeName] ?: "folder"
    } else when (treeName.substringAfter(".")) {
        "story.kts", "event.kts", "gui.kts", "kts" -> "file_kts"
        "gltf", "glb" -> "model"
        "png", "jpg", "jpeg" -> "file_image"
        "mp3", "ogg", "wav" -> "file_sound"
        "bedrock.json", "efkefc", "efkpkg" -> "file_effect"
        else -> "file"
    }
    val location = "hollowengine:textures/gui/icons/$file.png".rl
}