/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.structures

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.capabilities.SAABB
import ru.hollowhorizon.hollowengine.common.capabilities.StructuresCapability
import ru.hollowhorizon.hollowengine.common.registry.worldgen.structures.ModStructurePieces
import ru.hollowhorizon.hollowengine.common.registry.worldgen.structures.ModStructures
import java.util.*
import java.util.function.Predicate

class ScriptedStructure(settings: StructureSettings, val location: ResourceLocation) : Structure(settings) {

    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        val container = STRUCTURES[location] ?: return Optional.empty()

        if (!container.settings.check(context)) return Optional.empty()

        val chunkPos = context.chunkPos()

        val x = (chunkPos.x shl 4) + 7
        val z = (chunkPos.z shl 4) + 7
        var y: Int

        when (container.spawnMode) {
            SpawnMode.UNDERGROUND -> {
                val column = context.chunkGenerator.getBaseColumn(x, z, context.heightAccessor, context.randomState)

                var currentSize = 0
                var maxSize = 0
                var maxY = 0
                var currentY = context.heightAccessor.minBuildHeight
                while (currentY < context.heightAccessor.maxBuildHeight) {
                    val block = column.getBlock(currentY)
                    if (block.material.isReplaceable) {
                        currentSize++
                    } else {
                        if (currentSize > maxSize) {
                            maxSize = currentSize
                            maxY = currentY - currentSize
                        }
                        currentSize = 0
                    }
                    currentY++
                }

                if (maxSize < container.minSizeY) return Optional.empty()

                y = maxY - 1
            }

            SpawnMode.SURFACE -> {
                y = context.chunkGenerator.getFirstOccupiedHeight(
                    x,
                    z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor,
                    context.randomState
                )
            }

            SpawnMode.AIR -> {
                y = context.heightAccessor.maxBuildHeight - context.random.nextInt(50) - container.minSizeY
            }
        }

        y += container.yOffset

        val block = context.chunkGenerator.getBaseColumn(x, z, context.heightAccessor, context.randomState).getBlock(y)

        if (!block.fluidState.isEmpty) return Optional.empty()
        if (y < 0) return Optional.empty()

        return Optional.of(GenerationStub(BlockPos(x, y, z)) {
            it.addPiece(
                Piece(
                    context.structureTemplateManager(),
                    location,
                    BlockPos(x, y, z),
                    Rotation.getRandom(context.random.fork())
                )
            )
        })
    }

    override fun biomes(): HolderSet<Biome> {
        return HolderSet.direct(ForgeRegistries.BIOMES.values.map { Holder.direct(it) })
    }

    override fun generate(
        pRegistryAccess: RegistryAccess,
        pChunkGenerator: ChunkGenerator,
        pBiomeSource: BiomeSource,
        pRandomState: RandomState,
        pStructureTemplateManager: StructureTemplateManager,
        pSeed: Long,
        pChunkPos: ChunkPos,
        p_226604_: Int,
        pHeightAccessor: LevelHeightAccessor,
        pValidBiome: Predicate<Holder<Biome>>,
    ): StructureStart {
        val generatedStructures =
            ServerLifecycleHooks.getCurrentServer().overworld()[StructuresCapability::class].structures

        if (location.toString() in generatedStructures) return StructureStart.INVALID_START

        val optional = this.findGenerationPoint(
            GenerationContext(
                pRegistryAccess, pChunkGenerator, pBiomeSource,
                pRandomState, pStructureTemplateManager, pSeed,
                pChunkPos, pHeightAccessor, pValidBiome
            )
        )
        if (optional.isPresent) {
            val structurepiecesbuilder = optional.get().piecesBuilder
            val structurestart = StructureStart(this, pChunkPos, p_226604_, structurepiecesbuilder.build())
            if (structurestart.isValid) {
                val box = structurestart.pieces.first().boundingBox
                generatedStructures[location.toString()] =
                    SAABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                return structurestart
            }
        }

        return StructureStart.INVALID_START
    }

    override fun type() = ModStructures.TYPE.get()

    companion object {
        val CODEC: Codec<ScriptedStructure> =
            RecordCodecBuilder.create { builder ->
                builder
                    .group(
                        settingsCodec(builder),
                        ResourceLocation.CODEC.fieldOf("location").forGetter(ScriptedStructure::location)
                    )
                    .apply(builder, ::ScriptedStructure)
            }
    }

    class Piece : TemplateStructurePiece {

        constructor(
            manager: StructureTemplateManager,
            pLocation: ResourceLocation,
            pStartPos: BlockPos,
            pRotation: Rotation,
        ) : super(
            ModStructurePieces.PIECE.get(),
            0,
            manager,
            pLocation,
            pLocation.toString(),
            StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .setRotation(pRotation)
                .setMirror(Mirror.NONE),
            pStartPos
        )

        constructor(context: StructurePieceSerializationContext, tag: CompoundTag) : super(
            ModStructurePieces.PIECE.get(),
            tag,
            context.structureTemplateManager,
            {
                StructurePlaceSettings()
                    .addProcessor(JigsawReplacementProcessor.INSTANCE)
                    .setRotation(Rotation.valueOf(tag.getString("rotation")))
                    .setMirror(Mirror.NONE)
            })

        override fun addAdditionalSaveData(pContext: StructurePieceSerializationContext, pTag: CompoundTag) {
            super.addAdditionalSaveData(pContext, pTag)
            pTag.putString("rotation", rotation.name)
            HollowCore.LOGGER.info("structure data: {}", pTag)
        }

        override fun handleDataMarker(
            pName: String,
            pPos: BlockPos,
            pLevel: ServerLevelAccessor,
            pRandom: RandomSource,
            pBox: BoundingBox,
        ) {
        }
    }

}