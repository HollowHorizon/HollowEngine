package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.TrsTransformF
import de.fabmax.kool.util.Time
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntime
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeKey
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeRegistry
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationInstance
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.geary.components.AnimatorComponent
import ru.hollowhorizon.hollowengine.common.geary.components.MaterialOverrideLayerSpec
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import kotlin.math.max
import kotlin.math.min

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl), null)
class ModelAttachment(val flow: StateFlow<AnimatedModel>, parent: Attachment?) : Attachment(parent) {
    private val rebuildLock = Any()
    private var modelState: AnimatedModel = flow.value
    private var runtimeNodes: List<RuntimeNode> = emptyList()
    private var runtimeAnimations: Animations = Animations(emptyMap())
    private var runtimeMaterials: Set<Material> = emptySet()
    private var runtimeMaterialsList: List<Material> = emptyList()
    private var nodeIdToNode: Map<Int, RuntimeNode> = emptyMap()
    private var nodeIdToTransform = emptyMap<Int, TrsTransformF>()
    private val localAnimatorRuntime = AnimatorRuntime()
    private var animatorComponent: AnimatorComponent? = null
    private var animatorRuntimeKey: AnimatorRuntimeKey? = null
    private var animatorContext: AnimatorEvaluationContext = AnimatorEvaluationContext(0f, 0f)
    @Volatile
    private var compiledFor: AnimatedModel? = null
    @Volatile
    private var renderPipeline: ListRenderPipeline = ListRenderPipeline()
    @Volatile
    private var cachedBounds: Pair<Vec3f, Vec3f>? = null
    @Volatile
    private var cachedBoundsFrame = Int.MIN_VALUE

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

    fun configureAnimator(
        animator: AnimatorComponent?,
        key: AnimatorRuntimeKey?,
        context: AnimatorEvaluationContext,
    ) {
        animatorComponent = animator
        animatorRuntimeKey = key
        animatorContext = context
    }

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
            runtimeMaterialsList = model.materials.toList()
            nodeIdToNode = runtimeNodes.flatMap { it.walk() }.associateBy { it.definition.index }
            nodeIdToTransform = nodeIdToNode.mapValues { it.value.transform }
            renderPipeline = ListRenderPipeline().apply(this@ModelAttachment::collectCommands)
            cachedBounds = null
            cachedBoundsFrame = Int.MIN_VALUE
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
        val currentAnimator = animatorComponent

        transforms.forEach { (key, value) ->
            val base = indexedNodes[key]?.definition?.baseTransform ?: return@forEach
            value.set(base)
        }

        onUpdates.forEach { it() }

        if (currentAnimator != null) {
            val runtime = animatorRuntimeKey?.let(AnimatorRuntimeRegistry::get) ?: localAnimatorRuntime
            runtime.apply(
                animator = currentAnimator,
                rootNodes = runtimeNodes,
                animations = model.animations.associateBy { it.name },
                context = animatorContext.copy(deltaTime = dt),
            )
        } else {
            for (animation in currentAnimations) {
                animation.update(transforms, dt)
            }
        }

        val materialOverrides = currentAnimator?.layers
            ?.filterIsInstance<MaterialOverrideLayerSpec>()
            .orEmpty()
        if (materialOverrides.isNotEmpty()) {
            val materialsList = runtimeMaterialsList
            materialOverrides.forEach { layer ->
                layer.overrides.forEach { override ->
                    if (override.materialIndex in materialsList.indices) {
                        materialsList[override.materialIndex].texture = override.texture.rl
                    }
                }
            }
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

    fun calculateBoundsCached(frame: Int = Time.frameCount): Pair<Vec3f, Vec3f>? {
        if (cachedBoundsFrame == frame) return cachedBounds

        synchronized(rebuildLock) {
            if (cachedBoundsFrame == frame) return cachedBounds
            val bounds = calculateBoundsInternal()
            cachedBounds = bounds
            cachedBoundsFrame = frame
            return bounds
        }
    }

    private fun calculateBoundsInternal(): Pair<Vec3f, Vec3f>? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var hasBounds = false
        val source = MutableVec3f()
        val transformed = MutableVec3f()

        nodes.forEach { node ->
            node.walk().forEach { runtimeNode ->
                val matrix = runtimeNode.globalMatrix
                runtimeNode.definition.mesh?.primitives?.forEach { primitive ->
                    val localBounds = primitive.localBounds ?: return@forEach
                    val min = localBounds.first
                    val max = localBounds.second

                    fun update(x: Float, y: Float, z: Float) {
                        source.set(x, y, z)
                        matrix.transform(source, 1f, transformed)
                        minX = min(minX, transformed.x)
                        minY = min(minY, transformed.y)
                        minZ = min(minZ, transformed.z)
                        maxX = max(maxX, transformed.x)
                        maxY = max(maxY, transformed.y)
                        maxZ = max(maxZ, transformed.z)
                    }

                    update(min.x, min.y, min.z)
                    update(min.x, min.y, max.z)
                    update(min.x, max.y, min.z)
                    update(min.x, max.y, max.z)
                    update(max.x, min.y, min.z)
                    update(max.x, min.y, max.z)
                    update(max.x, max.y, min.z)
                    update(max.x, max.y, max.z)
                    hasBounds = true
                }
            }
        }

        if (!hasBounds) return null
        return Vec3f(minX, minY, minZ) to Vec3f(maxX, maxY, maxZ)
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

fun ModelAttachment.calculateBounds(): Pair<Vec3f, Vec3f>? = calculateBoundsCached()

private const val MODEL_BATCHING_PRIMITIVE_THRESHOLD = 48
private const val MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD = 24
private const val MODEL_BATCHING_TOTAL_CUBE_THRESHOLD = 128
private const val MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD = 16
private const val MODEL_BATCHING_DENSE_AVERAGE_CUBES = 4f
private const val MODEL_BATCHING_MIXED_AVERAGE_CUBES = 8f
