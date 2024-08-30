package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonObject
import net.minecraft.Util
//? if >=1.21 {

import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.repository.PackSource
import ru.hollowhorizon.hc.client.utils.mcText
import java.util.*

//?}
//? if >=1.20.1 {
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.IoSupplier
//?}
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterResourcePacksEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.InputStream

//? if >=1.20.1 {
object HollowEnginePack : PathPackResources(
    //? if >=1.21 {
    PackLocationInfo("HollowEngine Generated Resources", "HollowEngine Resources".mcText, PackSource.BUILT_IN, Optional.empty()),
    DirectoryManager.HOLLOW_ENGINE
    //?} else {
    /*"HollowEngine Folder Resources",
    DirectoryManager.HOLLOW_ENGINE,
    true
    *///?}
) {
    private val packMetadata = Util.make(JsonObject()) { json ->
        json.add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", 9)
        })
    }.toString()

    override fun getRootResource(vararg strings: String): IoSupplier<InputStream>? {
        return when (strings[0]) {
            PACK_META -> IoSupplier { packMetadata.byteInputStream() }
            else -> super.getRootResource(*strings)
        }
    }
}
//?} else {
/*import net.minecraft.server.packs.FolderPackResources
import java.io.FileNotFoundException

object HollowEnginePack : FolderPackResources(DirectoryManager.HOLLOW_ENGINE.toFile()) {
    private val packMetadata = Util.make(JsonObject()) { json ->
        json.add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", 9)
        })
    }.toString()

    override fun getRootResource(fileName: String): InputStream {
        return when (fileName) {
            PACK_META -> packMetadata.byteInputStream()
            else -> super.getRootResource(fileName) ?: throw FileNotFoundException("$fileName not found")
        }
    }
}
*///?}

@SubscribeEvent
fun addPackListeners(event: RegisterResourcePacksEvent) {
    event.addPack(HollowEnginePack)
}