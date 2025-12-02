package ru.hollowhorizon.hollowengine.client.models.internal.v2

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.util.Time
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationInstance
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.events.on
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl).model, null)
class ModelAttachment(model: Model, parent: Attachment?) : Attachment(parent) {
    init {
        if (model.isBlockBench) transform.rotate(180f.deg, Vec3f.Y_AXIS)
    }

    private val onUpdates = mutableListOf<ModelAttachment.() -> Unit>()
    val nodes = model.scenes[model.scene].nodes.map { RuntimeNode(it, this) }
    val animations = Animations(model.animations.associate { it.name to AnimationInstance(it) })

    private val nodeIdToNode = nodes.flatMap { it.walk() }.associateBy { it.definition.index }
    private val nodeIdToTransform = nodeIdToNode.mapValues { it.value.transform }
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
            if (!animation.enabled) continue
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

context(component: Component<LivingEntity>)
fun ModelAttachment.bindRenderer() {
    component.on<RenderEntityEvent.Pre>().onlyOwner { it.entity }.listen { event ->
        with(event) {
            poseStack.pushPose()

            var overlay = OverlayTexture.NO_OVERLAY
            if (this.entity is LivingEntity) {
                poseStack.mulPose(
                    Quaternionf().rotateY(
                        -Mth.rotLerp(
                            partialTicks,
                            entity.yBodyRotO,
                            entity.yBodyRot
                        ) * Mth.DEG_TO_RAD
                    )
                )
                overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
            }

            pipeline.render(RenderContext(poseStack, buffer, packedLight, overlay))
            poseStack.popPose()

            isCanceled = true
        }
    }
}

class Animations(private val map: Map<String, AnimationInstance>) : Collection<AnimationInstance> {
    operator fun get(name: String): AnimationInstance = map[name] ?: error("Animation $name not found")
    override val size: Int = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: AnimationInstance) = element in map.values

    override fun iterator(): Iterator<AnimationInstance> = map.values.iterator()

    override fun containsAll(elements: Collection<AnimationInstance>): Boolean = map.values.containsAll(elements)
}