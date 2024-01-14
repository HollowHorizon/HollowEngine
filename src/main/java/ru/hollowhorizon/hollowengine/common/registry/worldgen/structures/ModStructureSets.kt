package ru.hollowhorizon.hollowengine.common.registry.worldgen.structures

import net.minecraft.core.Registry
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import kotlin.random.Random

object ModStructureSets {
    val STRUCTURE_SETS: DeferredRegister<StructureSet> =
        DeferredRegister.create(Registry.STRUCTURE_SET_REGISTRY, "hollowengine")
    val SET: RegistryObject<StructureSet> = STRUCTURE_SETS.register("hollow_structures") {
        StructureSet(
            ModStructures.STRUCTURE.holder.get(),
            RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, Random.nextInt(99999999))
        )
    }
}