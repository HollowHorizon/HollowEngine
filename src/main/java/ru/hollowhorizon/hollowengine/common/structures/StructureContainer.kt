package ru.hollowhorizon.hollowengine.common.structures

import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.rl
import kotlin.random.Random

class StructureContainer {
    val settings = ArrayList<SpawnSettings>()
    val type = StructureType.BASIC
    val replacementType = ReplacementType.NOTHING
}

interface SpawnSettings {
    fun check(context: Structure.GenerationContext): Boolean
}

class StructureHeight(var height: Short, val lower: Boolean = true): SpawnSettings {
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

class StructureDimension(var dimension: String): SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return false
    }

}

class StructureBiome(var biome: String): SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return context.biomeSource.possibleBiomes().any { it.`is`(biome.rl) }
    }

}

class SpawnChance(var chance: Float): SpawnSettings {
    override fun check(context: Structure.GenerationContext): Boolean {
        return Random.nextFloat() > 1f - chance
    }

}


enum class SpawnMode {
    AIR, UNDERGROUND, SURFACE
}

enum class StructureType {
    BASIC, PUZZLE, DECORATION
}

enum class ReplacementType {
    AIR, NOTHING
}