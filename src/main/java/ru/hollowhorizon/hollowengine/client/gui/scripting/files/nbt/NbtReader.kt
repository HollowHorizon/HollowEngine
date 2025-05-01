package ru.hollowhorizon.hollowengine.client.gui.scripting.files.nbt

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

import java.util.*


object NbtReader {
    private fun findEntityByUUID(server: MinecraftServer, uuid: UUID, world: ServerLevel? = null): Entity? {
        val targetWorld = world ?: server.getLevel(Level.OVERWORLD) ?: return null
        return targetWorld.getEntity(uuid)
    }

    private fun getEntityNBT(entity: Entity): CompoundTag {
        val nbt = CompoundTag()
        entity.saveWithoutId(nbt)
        return nbt
    }

    fun getEntityNBTByUUID(server: MinecraftServer, uuid: UUID, world: ServerLevel? = null): CompoundTag? {
        val entity = findEntityByUUID(server, uuid, world) ?: return null
        return getEntityNBT(entity)
    }

    fun getItemNBT(itemStack: ItemStack): CompoundTag? {
        return itemStack.tag
    }

    fun getBlockEntityNBT(world: ServerLevel, pos: BlockPos?): CompoundTag? {
        val blockEntity = pos?.let { world.getBlockEntity(it) } ?: return null
        val nbt = blockEntity.saveWithoutMetadata()
        return nbt
    }
}


