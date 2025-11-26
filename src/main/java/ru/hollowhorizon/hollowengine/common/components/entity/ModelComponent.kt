package ru.hollowhorizon.hollowengine.common.components.entity

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.kool.addons.ResourceLocationRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.animations.ModelNode
import ru.hollowhorizon.hollowengine.client.models.internal.animations.NodeImpl
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.system.Cardinal
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.mutableLazy
import ru.hollowhorizon.hollowengine.common.utils.rl

@ComponentMeta("hollowengine:model_renderer")
class ModelComponent : Component<LivingEntity>() {
    var model: String by property { "hollowengine:models/entity/player_model.gltf" }
        .renderer { ResourceLocationRenderer(HollowModelManager.allModels.map { it.toString() }, "Путь к модели") }
        .copyOnDeath()
        .onChange { old, new ->
            if (!isLogicalClient) return@onChange
            root = createRootNode(model.rl)
            root.setup()

        }

    internal var root: ModelNode by mutableLazy { createRootNode(model.rl) }

    private fun createRootNode(location: ResourceLocation): ModelNode {
        val model = HollowModelManager.getOrCreate(location)

        return ModelNode().apply {
            if (model.model.isBlockBench) transform.rotate(90f.deg, Vec3f.Y_AXIS)
            children += model.model.scenes.flatMap { it.nodes.map { NodeImpl(this, it) } }
        }
    }

}

@Init
fun loadComponents() {

    Cardinal.on<RenderEntityEvent.Pre, ModelComponent> { model ->
        poseStack.pushPose()

        var overlay = OverlayTexture.NO_OVERLAY
        if (entity is LivingEntity) {
            poseStack.mulPose(
                Quaternionf().rotateY(-Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD)
            )
            overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f)
        }

        model.root.pipeline.render(
            RenderContext(poseStack, buffer, packedLight, overlay)
        )
        poseStack.popPose()

        isCanceled = true
    }
}