package ru.hollowhorizon.hollowengine.common.world.stuctures.schematics

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType

object SchematicPlacer {
    fun place(level: ServerLevel, origin: BlockPos, schematic: Schematic) {
        val blocks = schematic.blocks ?: return

        for (y in 0 until schematic.height) {
            for (z in 0 until schematic.length) {
                for (x in 0 until schematic.width) {
                    val index = SchematicParser.getIndex(x, y, z, schematic.width, schematic.length)
                    val paletteId = blocks.data.getOrNull(index) ?: continue
                    val blockInput = blocks.palette[paletteId] ?: continue

                    val targetPos = origin.offset(schematic.offset).offset(x, y, z)
                    blockInput.place(level, targetPos, 2)
                }
            }
        }

        blocks.blockEntities.forEach { beTag ->
            val posArray = beTag.getIntArray("Pos")
            if (posArray.size == 3) {
                val localPos = BlockPos(posArray[0], posArray[1], posArray[2])
                val targetPos = origin.offset(schematic.offset).offset(localPos)

                val blockEntity = level.getBlockEntity(targetPos)
                if (blockEntity != null) {
                    val finalTag = beTag.copy()
                    finalTag.putInt("x", targetPos.x)
                    finalTag.putInt("y", targetPos.y)
                    finalTag.putInt("z", targetPos.z)
                    //? if > 1.20.1 {
                    /*blockEntity.loadWithComponents(finalTag, level.registryAccess())
                    *///?} else {
                    blockEntity.load(finalTag)
                    //?}
                    blockEntity.setChanged()
                }
            }
        }

        schematic.entities.forEach { entityData ->
            val entityTag = entityData.nbt?.copy() ?: CompoundTag()
            entityTag.putString("id", entityData.id)

            val spawnPos = entityData.pos
                .add(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
                .add(schematic.offset.x.toDouble(), schematic.offset.y.toDouble(), schematic.offset.z.toDouble())

            val entity = EntityType.loadEntityRecursive(entityTag, level) { ent ->
                ent.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, ent.yRot, ent.xRot)
                ent
            }

            if (entity != null) {
                level.addFreshEntity(entity)
            }
        }
    }
}