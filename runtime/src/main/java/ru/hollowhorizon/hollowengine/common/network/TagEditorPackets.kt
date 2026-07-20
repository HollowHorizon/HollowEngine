@file:UseSerializers(ForResourceLocation::class)
package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.tags.TagDataManager
import ru.hollowhorizon.hollowengine.common.utils.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class SSyncTagDataPacket(val data: Map<ResourceLocation, Set<ResourceLocation>>, val registryType: String) : HollowPacket {
    override fun handle(player: Player) {
        // Update client-side representation if needed

    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class CUpdateTagPacket(
    val tagLocation: ResourceLocation,
    val entryLocation: ResourceLocation,
    val registryType: String,
    val action: TagAction
) : HollowPacket {
    enum class TagAction { ADD, REMOVE, DELETE_TAG, RESTORE_TAG, CREATE_TAG }

    override fun handle(player: Player) {
        if (!player.hasPermissions(PlayerPermissions.GAMEMASTER)) return

        when (action) {
            TagAction.ADD -> TagDataManager.addEntry(tagLocation, entryLocation, registryType)
            TagAction.REMOVE -> TagDataManager.removeEntry(tagLocation, entryLocation, registryType)
            TagAction.DELETE_TAG -> TagDataManager.deleteTag(tagLocation, registryType)
            TagAction.RESTORE_TAG -> TagDataManager.restoreTag(tagLocation, registryType)
            TagAction.CREATE_TAG -> TagDataManager.createTag(tagLocation, registryType)
        }
        TagDataManager.save()
        TagDataManager.syncAll()
    }
}