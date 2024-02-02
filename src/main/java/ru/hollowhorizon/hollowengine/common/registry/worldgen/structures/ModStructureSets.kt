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
}