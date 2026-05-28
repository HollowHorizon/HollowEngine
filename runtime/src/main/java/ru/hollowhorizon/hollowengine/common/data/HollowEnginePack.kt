package ru.hollowhorizon.hollowengine.common.data

import com.google.gson.JsonObject
import net.minecraft.SharedConstants
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterResourcePacksEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.literal
import java.io.InputStream
import java.util.*

object HollowEnginePack : PathPackResources(
    PackLocationInfo(
        "HollowEngine Folder Resources", "HollowEngine Folder Resources".literal, PackSource.BUILT_IN,
        Optional.empty()
    ), DirectoryManager.HOLLOW_ENGINE
) {
    private val packMetadata: String = JsonObject().apply {
        add("pack", JsonObject().apply {
            addProperty("description", "HollowEngine Folder Resources")
            addProperty("pack_format", SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major)
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
