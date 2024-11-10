package ru.hollowhorizon.hollowengine.client.gui.scripting

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTreeNodeFlags
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
    ImGui.sameLine()
    ImGui.setCursorPosX(ImGui.getCursorPosX() - 10)
    if (ImGui.treeNodeEx(treeName, flags)) {
        hovered = ImGui.isItemHovered()
        children.forEach { it.draw(drawFolderPopup, drawFilePopup) }

        ignore = true
        if (isFolder) ImGui.treePop()
    }
    hovered = hovered || (ImGui.isItemHovered() && !ignore)
    if (hovered && ImGui.isMouseClicked(1)) ImGui.openPopup(popupName)

    updatePayload()

    if (ImGui.isItemActivated() && ImGui.isMouseDoubleClicked(0) && !isFolder) {
        RequestFilePacket(treePath).send()
    }
}

private fun TreeNode.updatePayload() {
    if ((treePath.startsWith("assets") || treePath.startsWith("data")) && !isFolder && ImGui.beginDragDropSource()) {
        ImGui.setDragDropPayload("TREE", treePath, ImGuiCond.Once)
        ImGui.pushItemWidth(350f)
        Graphics.text(treePath.substringAfter('/').replaceFirst('/', ':'))
        ImGui.popItemWidth()
        ImGui.endDragDropSource()
    }
}

private val TreeNode.flags get() = if (isFolder) 0 else ImGuiTreeNodeFlags.NoTreePushOnOpen or ImGuiTreeNodeFlags.Leaf

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
    } else when (treeName.substringAfterLast(".")) {
        "kts" -> "file_kts"
        else -> "file"
    }
    val location = "hollowengine:textures/gui/icons/$file.png".rl

    val fs = ImGui.getFontSize().toFloat()
    Graphics.image(location, fs, fs)
}