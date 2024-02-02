package ru.hollowhorizon.hollowengine.common.structures

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import ru.hollowhorizon.hc.client.utils.rl
import kotlin.random.Random

val STRUCTURES = HashMap<ResourceLocation, StructureContainer>()

class StructureContainer {
    val settings: SpawnSettings = SpawnSettings { true }
    var spawnMode = SpawnMode.SURFACE
    var yOffset = 0
    val minSizeY = 10
}

fun interface SpawnSettings {
    fun check(context: Structure.GenerationContext): Boolean
}

fun SpawnSettings.and(other: SpawnSettings) = SpawnSettings {
    this@and.check(it) && other.check(it)
}

fun SpawnSettings.or(other: SpawnSettings) = SpawnSettings {
    this@or.check(it) || other.check(it)
}

class StructureHeight(var height: Short, val lower: Boolean = true) : SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        val chunkPos = context.chunkPos

        val x = (chunkPos.x shl 4) + 7
        val z = (chunkPos.z shl 4) + 7
        val y = context.chunkGenerator().getFirstOccupiedHeight(
            x,
            z,
            Heightmap.Types.WORLD_SURFACE_WG,
            context.heightAccessor,
            context.randomState()
        )
        return y > height
    }

}

class StructureDimension(var dimension: String) : SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return false
    }

}

class StructureBiome(var biome: String) : SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return context.biomeSource.possibleBiomes().any { it.`is`(biome.rl) }
    }

}

class SpawnChance(var chance: Float) : SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return Random.nextFloat() > 1f - chance
    }

}


enum class SpawnMode {
    AIR, UNDERGROUND, SURFACE
}