package ru.hollowhorizon.hollowengine.common.components.entity

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.kool.addons.ResourceLocationRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ClientModel
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ItemNode
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
            pipeline = createRootNode(model.rl)

        }

    internal var pipeline: RenderPipeline by mutableLazy { createRootNode(model.rl) }

    private fun createRootNode(location: ResourceLocation): RenderPipeline {
        val model = HollowModelManager.getOrCreate(location).model
        val clientModel = ClientModel(model)
        clientModel.transform.rotate(180f.deg, Vec3f.Y_AXIS)
        clientModel.animations["idle"]?.apply {
            enabled = true
            wrapMode = WrapMode.Loop
        }
        val pipeline = ListRenderPipeline()
        val body = clientModel.child("Unnamed_86").child("Model").child("Body").child("BodyUp")

        body.child("RightArm").child("RightHand").child("RightHandItem")
            .apply {
                val model = ClientModel(model, this)
                model.animations["dance2"]?.apply {
                    enabled = true
                    wrapMode = WrapMode.Loop
                }
                model.transform.scale(0.33f)
                    .rotate(180f.deg, Vec3f.Y_AXIS)
                    .rotate(90f.deg, Vec3f.X_AXIS)

                children.add(model)
            }
        body.child("LeftArm").child("LeftHand").child("LeftHandItem")
            .apply {
                val player = Minecraft.getInstance().player ?: return@apply
                children.add(ItemNode(player, EquipmentSlot.MAINHAND, this))
            }
        clientModel.collectCommands(pipeline)
        return pipeline
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

        model.pipeline.render(
            RenderContext(poseStack, buffer, packedLight, overlay)
        )
        poseStack.popPose()

        isCanceled = true
    }
}