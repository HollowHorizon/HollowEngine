package ru.hollowhorizon.hollowengine.common.structure

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.data.HollowEngineCorePack
import ru.hollowhorizon.hollowengine.common.structure.PoolBuilder.Companion.build
import ru.hollowhorizon.hollowengine.common.structure.StructureSetBuilder.Companion.build

class StructureBuilder(val structureType: String, val id: ResourceLocation) {
    private val mcBiomes: MutableList<String> = mutableListOf()
    private val pools: MutableList<String> = mutableListOf()
    private var structureSet: String = ""
    private var biomes: String = ""
    private var size: Int = 0
    private var maxDistanceFromCenter: Int = 0
    private var step: String = "surface_structures"
    private var terrainAdaptation: String? = null
    private var startHeight: String? = null
    private var maxInclusive: String? = null
    private var minInclusive: String? = null
    private var projectStartToHeightmap: String? = null
    private var useExpansionHack: Boolean = false
    private var spawnOverrides: String = ""

    fun addMcBiome(vararg biomes: StructureBiomes): StructureBuilder {
        this.mcBiomes.addAll(biomes.map { it.tagId })
        return this
    }

    fun addPool(pool: PoolBuilder): StructureBuilder {
        pools.add(pool.build())
        return this
    }

    fun setStructureSet(structureSet: StructureSetBuilder): StructureBuilder {
        this.structureSet = structureSet.build()
        return this
    }

    fun setSize(size: Int): StructureBuilder {
        this.size = size
        return this
    }

    fun setMaxDistanceFromCenter(maxDistanceFromCenter: Int): StructureBuilder {
        this.maxDistanceFromCenter = maxDistanceFromCenter
        return this
    }

    fun setStep(step: String): StructureBuilder {
        this.step = step
        return this
    }

    fun setTerrainAdaptation(terrainAdaptation: String): StructureBuilder {
        this.terrainAdaptation = terrainAdaptation
        return this
    }

    fun setStartHeight(startHeight: Int): StructureBuilder {
        this.startHeight = "{\"absolute\":$startHeight}"
        return this
    }

    fun setMaxInclusive(maxInclusive: String): StructureBuilder {
        this.maxInclusive = maxInclusive
        return this
    }

    fun setMinInclusive(minInclusive: String): StructureBuilder {
        this.minInclusive = minInclusive
        return this
    }

    fun setMaxInclusive(maxInclusive: Int): StructureBuilder {
        this.maxInclusive = "{\"below_top\":$maxInclusive}"
        return this
    }

    fun setMinInclusive(minInclusive: Int): StructureBuilder {
        this.minInclusive = "{\"above_bottom\":$minInclusive}"
        return this
    }

    fun setProjectStartToHeightmap(projectStartToHeightmap: String): StructureBuilder {
        this.projectStartToHeightmap = projectStartToHeightmap
        return this
    }

    fun setUseExpansionHack(useExpansionHack: Boolean): StructureBuilder {
        this.useExpansionHack = useExpansionHack
        return this
    }

    fun setSpawnOverrides(spawnOverrides: String): StructureBuilder {
        this.spawnOverrides = spawnOverrides
        return this
    }

    fun build() {
        fixBeforeBuild()
        val poolIds = mutableMapOf<ResourceLocation, String>()
        pools.forEachIndexed { index, pool ->
            poolIds["${id.namespace}:${id.path}/$index".rl] = pool
        }
        HollowEngineCorePack.apply {
            addStructure(id, StructureHelper.createStructure(
                structureType,
                poolIds.keys.first().toString(),
                size,
                maxDistanceFromCenter,
                biomes,
                step,
                terrainAdaptation,
                startHeight,
                maxInclusive,
                minInclusive,
                projectStartToHeightmap,
                useExpansionHack,
                spawnOverrides
            ))

            poolIds.forEach(::addTemplatePool)
        }
    }

    private fun fixBeforeBuild() {
        if (startHeight != null && (maxInclusive != null && minInclusive != null)) startHeight = null

        if (startHeight != null && ((maxInclusive != null && minInclusive == null) || (maxInclusive == null && minInclusive != null))) {
            maxInclusive = null
            minInclusive = null
        }

        if (startHeight == null && (maxInclusive == null && minInclusive == null)) throw IllegalStateException("Start height or min/max inclusive mustn't be null")
    }
}

class PoolBuilder(private val fallback: String) {
    companion object {
        fun PoolBuilder.build(): String {
            return StructureHelper.createPool(fallback, *elements.toTypedArray())
        }
    }

    private val elements: MutableList<String> = mutableListOf()

    fun addElement(location: String, processors: String, projection: String, elementType: String): PoolBuilder {
        elements.add(StructureHelper.createPoolElement(location, processors, projection, elementType))
        return this
    }
}

class StructureSetBuilder {
    companion object {
        fun StructureSetBuilder.build(): String = StructureHelper.createStructureSet(
            StructureHelper.createStructureSetPlacement(type, salt, spacing, separation),
            *structures.toTypedArray()
        )
    }
    private var type: String = "minecraft:random_spread"
    private var salt: Int? = null
    private var spacing: Int = 12
    private var separation: Int = 4
    private val structures: MutableList<String> = mutableListOf()

    fun setType(type: String): StructureSetBuilder {
        this.type = type
        return this
    }

    fun setSalt(salt: Int): StructureSetBuilder {
        this.salt = salt
        return this
    }

    fun setSpacing(spacing: Int): StructureSetBuilder {
        this.spacing = spacing
        return this
    }

    fun setSeparation(separation: Int): StructureSetBuilder {
        this.separation = separation
        return this
    }

    fun addStructure(structureId: String, weight: Int): StructureSetBuilder {
        structures.add(StructureHelper.createStructureSetStructure(structureId, weight))
        return this
    }
}
