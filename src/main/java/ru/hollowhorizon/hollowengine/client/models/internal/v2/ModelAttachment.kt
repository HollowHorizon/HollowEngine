package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.util.Time
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationInstance
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl), null)
class ModelAttachment(val flow: StateFlow<AnimatedModel>, parent: Attachment?) : Attachment(parent) {
    private val rebuildLock = Any()
    private var modelState: AnimatedModel = flow.value
    private var runtimeNodes: List<RuntimeNode> = emptyList()
    private var runtimeAnimations: Animations = Animations(emptyMap())
    private var runtimeMaterials: Set<Material> = emptySet()
    private var nodeIdToNode: Map<Int, RuntimeNode> = emptyMap()
    private var nodeIdToTransform = emptyMap<Int, de.fabmax.kool.scene.TrsTransformF>()
    @Volatile
    private var compiledFor: AnimatedModel? = null
    @Volatile
    private var renderPipeline: ListRenderPipeline = ListRenderPipeline()

    val model get() = modelState.model
    val nodes get() = runtimeNodes
    val animations get() = runtimeAnimations
    val materials get() = runtimeMaterials
    val pipeline: RenderPipeline
        get() {
            ensureCompiled(flow.value)
            return renderPipeline
        }

    init {
        ensureCompiled(flow.value)
        flow.onEach { ensureCompiled(it) }.launchIn(Minecraft.getInstance().coroutineScope)
    }

    val triangles get() =
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.positionsCount / 3 } ?: 0
        }

    val shapekeys get() =
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.morphTargets.size } ?: 0
        }

    private val onUpdates = mutableListOf<ModelAttachment.() -> Unit>()
    private val onPostUpdates = mutableListOf<ModelAttachment.() -> Unit>()

    fun onUpdate(action: ModelAttachment.() -> Unit) {
        onUpdates.add(action)
    }

    fun onPostUpdate(action: ModelAttachment.() -> Unit) {
        onPostUpdates.add(action)
    }

    private fun ensureCompiled(animated: AnimatedModel) {
        if (compiledFor === animated) return

        synchronized(rebuildLock) {
            if (compiledFor === animated) return

            if (animated.model.isBlockBench) {
                transform.rotation.set(180f.deg, Vec3f.Y_AXIS)
            }

            modelState = animated
            configurePrimitiveRenderPaths(animated)
            runtimeNodes = model.scenes.getOrNull(model.scene)?.nodes?.map { RuntimeNode(it, this) } ?: emptyList()
            runtimeAnimations = Animations(model.animations.associate { it.name to AnimationInstance(it) })
            runtimeMaterials = model.materials
            nodeIdToNode = runtimeNodes.flatMap { it.walk() }.associateBy { it.definition.index }
            nodeIdToTransform = nodeIdToNode.mapValues { it.value.transform }
            renderPipeline = ListRenderPipeline().apply(this@ModelAttachment::collectCommands)
            compiledFor = animated
        }
    }

    private fun configurePrimitiveRenderPaths(animated: AnimatedModel) {
        val allPrimitives = animated.model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.toList()
        val staticPrimitives = allPrimitives.filter { !it.hasSkinning && it.morphTargets.isEmpty() }

        val primitiveCount = staticPrimitives.size
        if (primitiveCount == 0) return

        val totalCubeCount = staticPrimitives.sumOf(Primitive::estimatedCubeCount)
        val averageCubesPerPrimitive = totalCubeCount.toFloat() / primitiveCount.toFloat()
        val preferBatching = primitiveCount >= MODEL_BATCHING_PRIMITIVE_THRESHOLD ||
            (primitiveCount >= MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD && averageCubesPerPrimitive <= MODEL_BATCHING_DENSE_AVERAGE_CUBES) ||
            (primitiveCount >= MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD &&
                totalCubeCount >= MODEL_BATCHING_TOTAL_CUBE_THRESHOLD &&
                averageCubesPerPrimitive <= MODEL_BATCHING_MIXED_AVERAGE_CUBES)

        val renderPath = if (preferBatching) Primitive.StaticRenderPath.BATCHING else Primitive.StaticRenderPath.PIPELINE
        staticPrimitives.forEach { it.setStaticRenderPath(renderPath) }
    }

    private fun update(dt: Float) {
        val transforms = nodeIdToTransform
        val indexedNodes = nodeIdToNode
        val currentAnimations = runtimeAnimations

        transforms.forEach { (key, value) ->
            val base = indexedNodes[key]?.definition?.baseTransform ?: return@forEach
            value.set(base)
        }

        onUpdates.forEach { it() }

        for (animation in currentAnimations) {
            animation.update(transforms, dt)
        }

        onPostUpdates.forEach { it() }
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        pipeline.onUpdate { update(if (IrisHelper.isShadowRendering()) 0f else Time.deltaT) }
        runtimeNodes.forEach { it.collectCommands(pipeline) }
    }

    fun child(name: String): RuntimeNode {
        ensureCompiled(flow.value)
        return runtimeNodes.single { it.name == name }
    }

    fun findNode(name: String): RuntimeNode? {
        ensureCompiled(flow.value)
        return runtimeNodes.asSequence().flatMap { it.walk().asSequence() }.firstOrNull { it.name == name }
    }
}

class Animations(private val map: Map<String, AnimationInstance>) : Collection<AnimationInstance> {
    operator fun get(name: String): AnimationInstance = map[name] ?: error("Animation $name not found")
    override val size: Int = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: AnimationInstance) = element in map.values

    override fun iterator(): Iterator<AnimationInstance> = map.values.iterator()

    override fun containsAll(elements: Collection<AnimationInstance>) = map.values.containsAll(elements)
}

private const val MODEL_BATCHING_PRIMITIVE_THRESHOLD = 48
private const val MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD = 24
private const val MODEL_BATCHING_TOTAL_CUBE_THRESHOLD = 128
private const val MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD = 16
private const val MODEL_BATCHING_DENSE_AVERAGE_CUBES = 4f
private const val MODEL_BATCHING_MIXED_AVERAGE_CUBES = 8f
