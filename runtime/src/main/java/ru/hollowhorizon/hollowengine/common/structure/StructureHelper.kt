package ru.hollowhorizon.hollowengine.common.structure

import kotlin.random.Random

object StructureHelper {
    @JvmStatic
    fun createStructure(
        type: String,
        startPool: String,
        size: Int,
        maxDistanceFromCenter: Int,
        biomes: String,
        step: String,
        terrainAdaptation: String?,
        startHeight: String?,
        maxInclusive: String?,
        minInclusive: String?,
        projectStartToHeightmap: String?,
        useExpansionHack: Boolean,
        spawnOverrides: String
    ): String = buildString {
        append("{")
        append('"').append("type").append('"').append(":").append('"').append(type).append('"').append(",")
        append('"').append("start_pool").append('"').append(":").append('"').append(startPool).append('"').append(",")
        append('"').append("size").append('"').append(":").append(size).append(",")
        append('"').append("max_distance_from_center").append('"').append(":").append(maxDistanceFromCenter).append(",")
        append('"').append("biomes").append('"').append(":").append('"').append(biomes).append('"').append(",")
        append('"').append("step").append('"').append(":").append('"').append(step).append('"').append(",")
        terrainAdaptation?.let { append('"').append("terrain_adaptation").append('"').append(":").append('"').append(it).append('"').append(",") }
        startHeight?.let { append('"').append("start_height").append('"').append(":").append(it).append(",") }
        maxInclusive?.let { append('"').append("max_inclusive").append('"').append(":").append('"').append(it).append('"').append(",") }
        minInclusive?.let { append('"').append("min_inclusive").append('"').append(":").append('"').append(it).append('"').append(",") }
        projectStartToHeightmap?.let { append('"').append("project_start_to_heightmap").append('"').append(":").append('"').append(it).append('"').append(",") }
        append('"').append("use_expansion_hack").append('"').append(":").append(useExpansionHack).append(",")
        append('"').append("spawn_overrides").append('"').append(":").append(spawnOverrides)
        append("}")
    }

    fun createPool(fallback: String, vararg elements: String) = buildString {
        append("{").append('"').append("fallback").append('"').append(":").append('"').append(fallback).append('"').append(",")
        append('"').append("elements").append('"').append(":[")
        elements.forEachIndexed { i, it -> append(it).also { if (i != elements.size - 1) append(",") } }
        append("]}")
    }

    fun createPoolElement(location: String, processors: String, projection: String, elementType: String) = buildString {
        append("{")
        append('"').append("location").append('"').append(":").append('"').append(location).append('"').append(",")
        append('"').append("processors").append('"').append(":").append('"').append(processors).append('"').append(",")
        append('"').append("projection").append('"').append(":").append('"').append(projection).append('"').append(",")
        append('"').append("element_type").append('"').append(":").append('"').append(elementType).append('"')
        append("}")
    }

    fun createStructureSet(placement: String, vararg structures: String) = buildString {
        append("{")
        append('"').append("structures").append('"').append(":[")
        structures.forEachIndexed { i, it -> append(it).also { if (i != structures.size - 1) append(",") } }
        append(']').append(',')
        append('"').append("placement").append('"').append(":").append(placement)
        append("}")
    }

    fun createStructureSetStructure(structureId: String, weight: Int) = buildString {
        append("{")
        append('"').append("structure").append('"').append(":").append('"').append(structureId).append('"').append(",")
        append('"').append("weight").append('"').append(":").append(weight)
        append("}")
    }
    
    fun createStructureSetPlacement(type: String, salt: Int?, spacing: Int, separation: Int) = buildString {
        append("{")
        append('"').append("type").append('"').append(":").append('"').append(type).append('"').append(",")
        append('"').append("salt").append('"').append(":").append(salt ?: Random.nextInt(800000000, 899999999)).append(",")
        append('"').append("spacing").append('"').append(":").append(spacing).append(",")
        append('"').append("separation").append('"').append(":").append(separation)
        append("}")
    }

    fun createProcessorBlock(type: String, vararg rules: String) = buildString {
        append("{")
        append('"').append("type").append('"').append(":").append('"').append(type).append('"').append(",")
        append('"').append("rules").append('"').append(":[")
        rules.forEachIndexed { i, it -> append(it).also { if (i != rules.size - 1) append(",") } }
        append("]")
        append("}")
    }

    fun createProcessorRule(inputPredicate: String, locationPredicate: String, outputState: String) = buildString {
        append("{")
        append('"').append("input_predicate").append('"').append(":").append(inputPredicate).append(",")
        append('"').append("location_predicate").append('"').append(":").append(locationPredicate).append(",")
        append('"').append("output_state").append('"').append(":").append('{').append(outputState).append('}')
        append("}")
    }
}
