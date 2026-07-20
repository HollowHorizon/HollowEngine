package ru.hollowhorizon.hollowengine.client.models.internal.v2

import kotlinx.coroutines.flow.StateFlow
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationClip
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntime
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeKey
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorRuntimeRegistry
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.geary.components.AnimatorComponent
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.utils.math.deg
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.math.max
import kotlin.math.min

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl), null)
class ModelAttachment(val flow: StateFlow<AnimatedModel>, parent: Attachment?, var entity: LivingEntity? = null) :
    Attachment(parent) {
    private val rebuildLock = Any()
    private var modelState: AnimatedModel = flow.value
    private var runtimeNodes: List<RuntimeNode> = emptyList()
    private var runtimeMaterials = ModelInstanceMaterials(modelState.model)
    private var nodeIdToNode: Map<Int, RuntimeNode> = emptyMap()
    private val localAnimatorRuntime = AnimatorRuntime()
    private var animatorComponent: AnimatorComponent? = null
    private var animatorRuntimeKey: AnimatorRuntimeKey? = null
    private var animatorContext: AnimatorEvaluationContext = AnimatorEvaluationContext(0f, 0f)
    private var lastAnimationFrame = Long.MIN_VALUE
    private var poseDirty = true

    @Volatile
    private var compiledFor: AnimatedModel? = null

    @Volatile
    private var renderPipeline: ListRenderPipeline = ListRenderPipeline()

    @Volatile
    private var cachedBounds: Pair<Vec3f, Vec3f>? = null

    @Volatile
    private var cachedBoundsFrame = Long.MIN_VALUE

    val model: Model
        get() {
            ensureCompiled(flow.value)
            return modelState.model
        }
    val nodes: List<RuntimeNode>
        get() {
            ensureCompiled(flow.value)
            return runtimeNodes
        }
    val animations: Collection<AnimationClip>
        get() {
            ensureCompiled(flow.value)
            return modelState.animations.values
        }
    val materials: List<Material>
        get() {
            ensureCompiled(flow.value)
            return runtimeMaterials.values
        }
    val pipeline: RenderPipeline
        get() {
            ensureCompiled(flow.value)
            return renderPipeline
        }

    init {
        ensureCompiled(flow.value)
    }

    val triangles
        get() =
            model.walkNodes().sumOf {
                it.mesh?.primitives?.sumOf { it.positionsCount / 3 } ?: 0
            }

    val shapekeys
        get() =
            model.walkNodes().sumOf {
                it.mesh?.primitives?.sumOf { it.morphTargets.size } ?: 0
            }

    fun configureAnimator(
        animator: AnimatorComponent?,
        key: AnimatorRuntimeKey?,
        context: AnimatorEvaluationContext,
    ) {
        if (animatorComponent != animator || animatorRuntimeKey != key) {
            poseDirty = true
        }
        animatorComponent = animator
        animatorRuntimeKey = key
        animatorContext = context
    }

    fun animationTime(layerId: String): Float? {
        val runtime = animatorRuntimeKey?.let(AnimatorRuntimeRegistry::get) ?: localAnimatorRuntime
        return runtime.stateFor(layerId)?.time
    }

    private fun ensureCompiled(animated: AnimatedModel) {
        if (compiledFor === animated) return

        synchronized(rebuildLock) {
            if (compiledFor === animated) return

            val model = animated.model
            if (model.isBlockBench) {
                transform.rotation.set(180f.deg, Vec3f.Y_AXIS)
            }

            modelState = animated
            configurePrimitiveRenderPaths(animated)
            runtimeMaterials = ModelInstanceMaterials(model)
            runtimeNodes = model.scenes.getOrNull(model.scene)?.nodes?.map {
                RuntimeNode(it, this, runtimeMaterials::resolve)
            } ?: emptyList()
            nodeIdToNode = runtimeNodes.flatMap { it.walk() }.associateBy { it.definition.index }
            nodeIdToNode.values.forEach(::customizeNode)
            renderPipeline = ListRenderPipeline().apply(this@ModelAttachment::collectCommands)
            cachedBounds = null
            cachedBoundsFrame = Long.MIN_VALUE
            poseDirty = true
            compiledFor = animated
        }
    }

    private fun customizeNode(node: RuntimeNode) {
        when (node.name) {
            "RightHandItem" -> node.attachments.add(ItemNode({ entity }, EquipmentSlot.MAINHAND, node))
            "LeftHandItem" -> node.attachments.add(ItemNode({ entity }, EquipmentSlot.OFFHAND, node))
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
                primitiveCount >= MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD &&
                averageCubesPerPrimitive <= MODEL_BATCHING_DENSE_AVERAGE_CUBES ||
                primitiveCount >= MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD &&
                totalCubeCount >= MODEL_BATCHING_TOTAL_CUBE_THRESHOLD &&
                averageCubesPerPrimitive <= MODEL_BATCHING_MIXED_AVERAGE_CUBES

        val renderPath =
            if (preferBatching) Primitive.StaticRenderPath.BATCHING else Primitive.StaticRenderPath.PIPELINE
        staticPrimitives.forEach { it.setStaticRenderPath(renderPath) }
    }

    fun prepareFrame(dt: Float, frame: Long = TickHandler.renderFrame) {
        ensureCompiled(flow.value)
        val shouldAdvance = dt > 0f && lastAnimationFrame != frame
        if (poseDirty || shouldAdvance) {
            updateAnimation(if (shouldAdvance) dt else 0f)
            poseDirty = false
        }
        if (shouldAdvance) {
            lastAnimationFrame = frame
        }

        updateGlobalMatrix()
        runtimeNodes.forEach(RuntimeNode::updateHierarchyMatrices)
    }

    private fun updateAnimation(dt: Float) {
        val indexedNodes = nodeIdToNode
        val currentAnimator = animatorComponent

        indexedNodes.values.forEach(RuntimeNode::resetPose)

        if (currentAnimator != null) {
            val runtime = animatorRuntimeKey?.let(AnimatorRuntimeRegistry::get) ?: localAnimatorRuntime
            runtime.apply(
                animator = currentAnimator,
                rootNodes = runtimeNodes,
                runtimeNodes = indexedNodes,
                animations = modelState.animations,
                context = animatorContext.copy(deltaTime = dt),
            )
        } else if (animatorRuntimeKey == null) {
            localAnimatorRuntime.clear()
        }

        cachedBoundsFrame = Long.MIN_VALUE
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
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

    fun calculateBoundsCached(frame: Long = TickHandler.renderFrame): Pair<Vec3f, Vec3f>? {
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

fun ModelAttachment.calculateBounds(): Pair<Vec3f, Vec3f>? = calculateBoundsCached()

private const val MODEL_BATCHING_PRIMITIVE_THRESHOLD = 48
private const val MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD = 24
private const val MODEL_BATCHING_TOTAL_CUBE_THRESHOLD = 128
private const val MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD = 16
private const val MODEL_BATCHING_DENSE_AVERAGE_CUBES = 4f
private const val MODEL_BATCHING_MIXED_AVERAGE_CUBES = 8f
