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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hollowengine.common.capabilities.StructuresCapability
import ru.hollowhorizon.hollowengine.common.registry.worldgen.structures.ModStructurePieces
import ru.hollowhorizon.hollowengine.common.registry.worldgen.structures.ModStructures
import java.util.*
import java.util.function.Predicate


class ScriptedStructure(settings: StructureSettings, val location: ResourceLocation) : Structure(settings) {

    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        val chunkPos = context.chunkPos()

        val x = (chunkPos.x shl 4) + 7
        val z = (chunkPos.z shl 4) + 7
        val y = context.chunkGenerator().getFirstOccupiedHeight(
            x,
            z,
            Heightmap.Types.WORLD_SURFACE_WG,
            context.heightAccessor,
            context.randomState()
        )

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
        pValidBiome: Predicate<Holder<Biome>>
    ): StructureStart {
        val generatedStructures =
            ServerLifecycleHooks.getCurrentServer().overworld()[StructuresCapability::class].structures

        if (location.toString() in generatedStructures) return StructureStart.INVALID_START

        val result = super.generate(pRegistryAccess, pChunkGenerator, pBiomeSource, pRandomState, pStructureTemplateManager, pSeed, pChunkPos, p_226604_, pHeightAccessor, pValidBiome)

        if(result.isValid) generatedStructures += location.toString()

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
            };
    }

    class Piece : TemplateStructurePiece {

        constructor(
            manager: StructureTemplateManager,
            pLocation: ResourceLocation,
            pStartPos: BlockPos,
            pRotation: Rotation
        ) : super(
            ModStructurePieces.PIECE.get(),
            0,
            manager,
            pLocation,
            pLocation.toString(),
            StructurePlaceSettings().setRotation(pRotation).setMirror(Mirror.NONE), pStartPos
        )

        constructor(context: StructurePieceSerializationContext, tag: CompoundTag) : super(
            ModStructurePieces.PIECE.get(),
            tag,
            context.structureTemplateManager,
            {
                StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("rotation"))).setMirror(Mirror.NONE)
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
            pBox: BoundingBox
        ) {
        }
    }

}