package ru.hollowhorizon.hollowengine.common.world.stuctures.schematics

import net.minecraft.commands.arguments.blocks.BlockInput
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.HollowCore

object SchematicParser {

    fun parse(root: CompoundTag): Schematic {
        val schemTag = root.getCompound("Schematic")

        val version = schemTag.getInt("Version")
        val dataVersion = schemTag.getInt("DataVersion")

        val width = schemTag.getShort("Width")
        val height = schemTag.getShort("Height")
        val length = schemTag.getShort("Length")

        val offset = if (schemTag.contains("Offset")) {
            val array = schemTag.getIntArray("Offset")
            BlockPos(array[0], array[1], array[2])
        } else {
            BlockPos.ZERO
        }

        val blockData = if (schemTag.contains("Blocks")) {
            val blocksTag = schemTag.getCompound("Blocks")
            val paletteTag = blocksTag.getCompound("Palette")

            val paletteMap = mutableMapOf<Int, BlockInput>()
            paletteTag.allKeys.forEach { key ->
                val key = key.replace("short_grass", "grass")
                try {
                    val state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, true)

                    paletteMap[paletteTag.getInt(key)] = BlockInput(state.blockState, state.properties.keys, state.nbt)
                } catch (e: Exception) {
                    HollowCore.LOGGER.error("Error parsing $key", e)
                    paletteMap[paletteTag.getInt(key)] = BlockInput(Blocks.BEDROCK.defaultBlockState(), emptySet(), null)
                }
            }

            val rawData = blocksTag.getByteArray("Data")
            val decodedData = VarIntSerializer.readVarIntArray(rawData, width * height * length)

            val blockEntities = mutableListOf<CompoundTag>()
            if (blocksTag.contains("BlockEntities")) {
                val beList = blocksTag.getList("BlockEntities", Tag.TAG_COMPOUND.toInt())
                for (i in 0 until beList.size) {
                    blockEntities.add(beList.getCompound(i))
                }
            }

            BlockData(paletteMap, decodedData, blockEntities)
        } else null

        val biomeData = if (schemTag.contains("Biomes")) {
            val biomesTag = schemTag.getCompound("Biomes")
            val paletteTag = biomesTag.getCompound("Palette")

            val paletteMap = mutableMapOf<Int, String>()
            paletteTag.allKeys.forEach { key ->
                paletteMap[paletteTag.getInt(key)] = key
            }

            val rawData = biomesTag.getByteArray("Data")
            val decodedData = VarIntSerializer.readVarIntArray(rawData, width * height * length)

            BiomeData(paletteMap, decodedData)
        } else null

        val entities = mutableListOf<EntityData>()
        if (schemTag.contains("Entities")) {
            val entityList = schemTag.getList("Entities", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until entityList.size) {
                val entTag = entityList.getCompound(i)
                val posList = entTag.getList("Pos", Tag.TAG_DOUBLE.toInt())
                val pos = Vec3(posList.getDouble(0), posList.getDouble(1), posList.getDouble(2))
                val id = entTag.getString("Id")
                val data = if (entTag.contains("Data")) entTag.getCompound("Data") else null

                entities.add(EntityData(id, pos, data))
            }
        }

        return Schematic(
            version, dataVersion, width, height, length,
            offset, blockData, biomeData, entities
        )
    }

    fun getIndex(x: Int, y: Int, z: Int, width: Short, length: Short): Int {
        return x + z * width + y * width * length
    }
}