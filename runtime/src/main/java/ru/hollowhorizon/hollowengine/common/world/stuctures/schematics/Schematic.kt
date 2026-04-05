package ru.hollowhorizon.hollowengine.common.world.stuctures.schematics

import net.minecraft.commands.arguments.blocks.BlockInput
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3

data class Schematic(
    val version: Int,
    val dataVersion: Int,
    val width: Short,
    val height: Short,
    val length: Short,
    val offset: BlockPos,
    val blocks: BlockData?,
    val biomes: BiomeData?,
    val entities: List<EntityData>
)

data class BlockData(
    val palette: MutableMap<Int, BlockInput>,
    val data: List<Int>,
    val blockEntities: List<CompoundTag>
)

data class BiomeData(
    val palette: Map<Int, String>,
    val data: List<Int>
)

data class EntityData(
    val id: String,
    val pos: Vec3,
    val nbt: CompoundTag?
)