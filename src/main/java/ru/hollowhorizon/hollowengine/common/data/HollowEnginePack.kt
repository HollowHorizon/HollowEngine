package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonObject
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.IoSupplier
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterResourcePacksEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.InputStream

object HollowEnginePack : PathPackResources("HollowEngine Folder Resources", DirectoryManager.HOLLOW_ENGINE, true) {
    private val packMetadata: String = JsonObject().apply {
        add("pack", JsonObject().apply {
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

@SubscribeEvent
fun addPackListeners(event: RegisterResourcePacksEvent) {
    event.addPack(HollowEnginePack)
}