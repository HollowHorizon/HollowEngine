package ru.hollowhorizon.hollowengine.client.models.internal


import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.controller.Controller
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext


typealias NodeRenderer = (LivingEntity, PoseStack, Node, MultiBufferSource, Int) -> Unit

class ModelData(
    val leftHand: ItemStack?,
    val rightHand: ItemStack?,
    val itemInHandRenderer: ItemInHandRenderer?,
    val entity: LivingEntity?,
)

class AnimatedModel(val model: Model, val animations: Map<String, Animation> = mapOf()) {

    val nodes = model.walkNodes().toList()
    var visuals: NodeRenderer = { _, _, _, _, _ -> }

    fun update(controller: Controller, query: MolangContext, time: Float) {
        try {
            nodes.forEach {
                it.transform.set(it.baseTransform)
                controller.update(it, query, time)
            }
        } catch (e: Exception) {
            HollowCore.LOGGER.error("Error while updating animations!", e)
            controller.layers.clear()
        }
    }

    fun render(
        stack: PoseStack,
        source: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        model.pipeline.render(RenderContext(stack, source, light, overlay))
    }

    fun destroy() {
        model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.forEach(Primitive::destroy)
    }
}