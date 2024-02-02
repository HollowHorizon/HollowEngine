package ru.hollowhorizon.hollowengine.common.registry.worldgen.structures

import net.minecraft.core.Registry
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import ru.hollowhorizon.hollowengine.common.structures.ScriptedStructure.Piece

object ModStructurePieces {

    val STRUCTURE_PIECES: DeferredRegister<StructurePieceType> =
        DeferredRegister.create(Registry.STRUCTURE_PIECE_REGISTRY, "hollowengine")
    val PIECE: RegistryObject<StructurePieceType> = STRUCTURE_PIECES.register("hollow_piece") {
        StructurePieceType(::Piece)
    }
}