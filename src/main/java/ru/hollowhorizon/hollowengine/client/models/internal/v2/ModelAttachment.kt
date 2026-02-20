package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.util.Time
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationInstance
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl), null)
class ModelAttachment(val flow: StateFlow<AnimatedModel>, parent: Attachment?) : Attachment(parent) {
    val model get() = flow.value.model

    init {
        flow.onEach {
            if(it.model.isBlockBench) {
                transform.rotation.set(180f.deg, Vec3f.Y_AXIS)
            }
        }.launchIn(Minecraft.getInstance().coroutineScope)
    }

    val triangles by lazy {
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.positionsCount / 3 } ?: 0
        }
    }
    val shapekeys by lazy {
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.morphTargets.size } ?: 0
        }
    }
    private val onUpdates = mutableListOf<ModelAttachment.() -> Unit>()
    val nodes = model.scenes.getOrNull(model.scene)?.nodes?.map { RuntimeNode(it, this) } ?: emptyList()
    val animations = Animations(model.animations.associate { it.name to AnimationInstance(it) })
    val materials = model.materials
    private val nodeIdToNode = nodes.flatMap { it.walk() }.associateBy { it.definition.index }
    private val nodeIdToTransform = nodeIdToNode.mapValues { it.value.transform }

    @PublishedApi
    internal val pipeline by lazy {
        ListRenderPipeline().apply(::collectCommands)
    }


    fun onUpdate(action: ModelAttachment.() -> Unit) {
        onUpdates.add(action)
    }

    private fun update(dt: Float) {
        nodeIdToTransform.forEach { (key, value) ->
            val base = nodeIdToNode[key]?.definition?.baseTransform ?: return@forEach
            value.set(base)
        }

        onUpdates.forEach { it() }

        for (animation in animations) {
            animation.update(nodeIdToTransform, dt)
        }
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        pipeline.onUpdate { update(if (IrisHelper.isShadowRendering()) 0f else Time.deltaT) }
        nodes.forEach { it.collectCommands(pipeline) }
    }

    fun child(name: String) = nodes.single { it.name == name }
}

class Animations(private val map: Map<String, AnimationInstance>) : Collection<AnimationInstance> {
    operator fun get(name: String): AnimationInstance = map[name] ?: error("Animation $name not found")
    override val size: Int = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: AnimationInstance) = element in map.values

    override fun iterator(): Iterator<AnimationInstance> = map.values.iterator()

    override fun containsAll(elements: Collection<AnimationInstance>): Boolean = map.values.containsAll(elements)
}