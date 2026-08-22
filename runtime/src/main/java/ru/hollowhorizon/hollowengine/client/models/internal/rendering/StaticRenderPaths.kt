package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive

/**
 * Picks how the unskinned parts of a model are drawn.
 *
 * Many small primitives are cheaper batched into one draw; a few large ones are cheaper drawn straight
 * through the pipeline. The choice belongs to the model rather than to an instance of it: the render
 * path lives on the shared primitive, so every instance would otherwise decide it again for the same
 * geometry.
 */
fun Model.configureStaticRenderPaths() {
    val staticPrimitives = nodes.mapNotNull { it.mesh }
        .flatMap { it.primitives }
        .filter { !it.hasSkinning && it.morphTargets.isEmpty() }

    val primitiveCount = staticPrimitives.size
    if (primitiveCount == 0) return

    val totalCubeCount = staticPrimitives.sumOf(Primitive::estimatedCubeCount)
    val averageCubesPerPrimitive = totalCubeCount.toFloat() / primitiveCount.toFloat()
    val preferBatching = primitiveCount >= BATCHING_PRIMITIVE_THRESHOLD ||
            primitiveCount >= DENSE_PRIMITIVE_THRESHOLD && averageCubesPerPrimitive <= DENSE_AVERAGE_CUBES ||
            primitiveCount >= MIXED_PRIMITIVE_THRESHOLD &&
            totalCubeCount >= TOTAL_CUBE_THRESHOLD &&
            averageCubesPerPrimitive <= MIXED_AVERAGE_CUBES

    val renderPath = if (preferBatching) Primitive.StaticRenderPath.BATCHING else Primitive.StaticRenderPath.PIPELINE
    staticPrimitives.forEach { it.setStaticRenderPath(renderPath) }
}

private const val BATCHING_PRIMITIVE_THRESHOLD = 48
private const val DENSE_PRIMITIVE_THRESHOLD = 24
private const val TOTAL_CUBE_THRESHOLD = 128
private const val MIXED_PRIMITIVE_THRESHOLD = 16
private const val DENSE_AVERAGE_CUBES = 4f
private const val MIXED_AVERAGE_CUBES = 8f
