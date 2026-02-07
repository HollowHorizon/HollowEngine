package ru.hollowhorizon.hollowengine.client.models.internal


import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.controller.Controller
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext


class AnimatedModel(val model: Model) {
    val animations: Map<String, Animation> = model.animations.associateBy { it.name }
    val nodes = model.walkNodes().toList()

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

    companion object {
        val EMPTY = AnimatedModel(Model(0, listOf(), setOf(), listOf()))
    }
}