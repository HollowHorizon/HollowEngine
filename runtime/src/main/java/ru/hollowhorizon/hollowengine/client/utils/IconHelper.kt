package ru.hollowhorizon.hollowengine.client.utils

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.generated.Assets

object IconHelper {
    val Icons = Assets.Hollowengine.Textures.Gui.Icons

    fun forPath(path: String, isFolder: Boolean = false, isOpened: Boolean = false): ResourceLocation {
        return when {
            isFolder -> {
                if(isOpened) {
                    when (path) {
                        "assets" -> Icons.FOLDER_ASSETS_OPEN
                        "data" -> Icons.FOLDER_DATA_OPEN
                        "scripts" -> Icons.FOLDER_SCRIPTS_OPEN
                        "npcs" -> Icons.FOLDER_NPCS_OPEN
                        "camera" -> Icons.FOLDER_CAMERA_OPEN
                        else -> Icons.FOLDER_OPEN
                    }
                } else {
                    when (path) {
                        "assets" -> Icons.FOLDER_ASSETS
                        "data" -> Icons.FOLDER_DATA
                        "scripts" -> Icons.FOLDER_SCRIPTS
                        "npcs" -> Icons.FOLDER_NPCS
                        "camera" -> Icons.FOLDER_CAMERA
                        else -> Icons.FOLDER
                    }
                }
            }

            else -> {
                when (path.substringAfterLast('.')) {
                    "bc" -> Icons.FILE_CODEBLOCKS
                    "zip", "rar", "jar" -> Icons.FILE_ZIP
                    "gltf", "glb", "fbx", "geo.json", "obj" -> Icons.FILE_MODEL
                    "kts", "kt" -> Icons.FILE_KTS
                    "png", "jpg", "jpeg", "gif" -> Icons.FILE_IMAGE
                    "ogg", "mp3", "wav" -> Icons.FILE_SOUND
                    "mp4", "avi", "mov" -> Icons.FILM
                    else -> Icons.FILE
                }
            }
        }
    }
}