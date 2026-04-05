package ru.hollowhorizon.hollowengine.common.objects.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.hypot

open class HollowBlockEntity(type: BlockEntityType<*>, pos: BlockPos, blockState: BlockState) :
    BlockEntity(type, pos, blockState) {
    override fun setChanged() {
        super.setChanged()
        syncForNearbyPlayers()
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = ClientboundBlockEntityDataPacket.create(this)

    //? if >= 1.21 {
    /*override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithFullMetadata(registries)
    }
    *///?} else {
    

    override fun getUpdateTag(): CompoundTag = this.saveWithFullMetadata()
    //?}

    private fun syncForNearbyPlayers() {
        val level = this.level ?: return // check nonnull
        val packet = this.updatePacket ?: return

        val players = level.players()
        val pos = this.blockPos

        players.forEach {
            if (it is ServerPlayer) {
                if (hypot(it.x - (pos.x + 0.5), it.z - (pos.z + 0.5)) < 64)
                    it.connection.send(packet)
            }
        }
    }
}