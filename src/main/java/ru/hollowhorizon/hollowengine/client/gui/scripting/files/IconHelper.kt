package ru.hollowhorizon.hollowengine.client.gui.scripting.files

object IconHelper {
    fun forPath(path: String, isFolder: Boolean = false, isOpened: Boolean = false): String {
        val baseDir = "hollowengine:textures/gui/icons"

        return "$baseDir/" + when {
            isFolder -> {
                val type = when(path) {
                    "assets" -> "folder_assets"
                    "data" -> "folder_data"
                    "scripts" -> "folder_scripts"
                    "npcs" -> "folder_npcs"
                    "camera" -> "folder_camera"
                    else -> "folder"
                }

                if (isOpened) type + "_open"
                else type
            }

            else -> {
                when (path.substringAfterLast('.')) {
                    "kts", "kt" -> "file_kts"
                    "png", "jpg", "jpeg", "gif" -> "file_image"
                    "ogg", "mp3", "wav" -> "file_sound"
                    else -> "file"
                }
            }
        } + ".png"
    }
}