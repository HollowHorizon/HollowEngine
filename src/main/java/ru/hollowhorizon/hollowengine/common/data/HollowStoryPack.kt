package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonObject
import net.minecraft.Util
import net.minecraft.server.packs.FolderPackResources
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.InputStream

object HollowStoryPack : FolderPackResources(DirectoryManager.HOLLOW_ENGINE) {

    val PACK_META_BYTES = Util.make(JsonObject()) { json ->
        json.add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", 9)
        })
    }.toString()


    override fun getResource(pResourcePath: String): InputStream {
        return when (pResourcePath) {
            PACK_META -> PACK_META_BYTES.byteInputStream()
            else -> super.getResource(pResourcePath)
        }
    }

    override fun hasResource(pResourcePath: String): Boolean {
        return pResourcePath == PACK_META || super.hasResource(pResourcePath)
    }
}