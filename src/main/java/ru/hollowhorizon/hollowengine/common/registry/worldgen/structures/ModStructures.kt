package ru.hollowhorizon.hollowengine.common.registry.worldgen.structures

import com.google.common.base.Supplier
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.data.BuiltinRegistries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.structures.ScriptedStructure
import kotlin.random.Random


object ModStructures {
    val STRUCTURES: DeferredRegister<Structure> = DeferredRegister.create(Registry.STRUCTURE_REGISTRY, "hollowengine")
    val STRUCTURE_TYPES: DeferredRegister<StructureType<*>> =
        DeferredRegister.create(Registry.STRUCTURE_TYPE_REGISTRY, "hollowengine")


    val TYPE: RegistryObject<StructureType<*>> =
        STRUCTURE_TYPES.register("hollow_structure_type") { StructureType { ScriptedStructure.CODEC } }

    fun <T : Structure> addStructure(location: String, value: Supplier<T>) =
        STRUCTURES.register(location, value).apply {
            ModStructureSets.STRUCTURE_SETS.register(
                location + "_set"
            ) {
                StructureSet(
                    this.holder.get() as Holder<Structure>,
                    RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, Random.nextInt(99999999))
                )
            }
        }


    fun createSettings(location: TagKey<Biome>) = Structure.StructureSettings(
            BuiltinRegistries.BIOME.getOrCreateTag(
                location
            ),
            mapOf(),
            GenerationStep.Decoration.SURFACE_STRUCTURES,
            TerrainAdjustment.BEARD_THIN
        )

    val STRUCTURE: RegistryObject<Structure> = STRUCTURES.register("hollow_structure") {
        ScriptedStructure(createSettings(ModBiomeTags.HOLLOW_STRUCTURE), "hollowengine:camp".rl)
    }

}