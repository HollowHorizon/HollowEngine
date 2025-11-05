package ru.hollowhorizon.hollowengine.common.components.entity

import de.fabmax.kool.util.Time
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.kool.addons.ResourceLocationRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animator
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.system.Cardinal
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.mutableLazy
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

@ComponentMeta("hollowengine:model_renderer")
class ModelComponent : Component<LivingEntity>() {
    var model: String by property { "hollowengine:models/entity/player_model.gltf" }
        .renderer { ResourceLocationRenderer(HollowModelManager.allModels.map { it.toString() }, "Путь к модели") }
        .copyOnDeath()
        .onChange { old, new ->
            if (!isLogicalClient) return@onChange
            internalModel = HollowModelManager.getOrCreate(new.rl)
            animator = Animator(internalModel, owner)
        }

    internal var internalModel: AnimatedModel by mutableLazy { HollowModelManager.getOrCreate(model.rl) }
    internal var animator by mutableLazy {
        val player = Minecraft.getInstance().player ?: error("Player not initialized")
        Animator(internalModel, player)
    }
}

@Init
fun loadComponents() {

    Cardinal.on<RenderEntityEvent.Pre, ModelComponent> { model ->
        poseStack.pushPose()
        model.animator.apply {
            //configure()
            reset()
            //onUpdate()
            if (IrisHelper.isShadowRendering()) update(0.0f)
            else update(Time.deltaT) // Minecraft.getInstance().deltaFrameTime * 50 / 1000f
        }

        if (model.internalModel.model.isBlockBench || true) poseStack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))
        var overlay = OverlayTexture.NO_OVERLAY
        if (entity is LivingEntity) {
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

        model.internalModel.render(
            poseStack,
            ModelData(null, null, null, null),
            { it.toTexture().id },
            buffer,
            packedLight,
            overlay
        )
        poseStack.popPose()

        isCanceled = true
    }
}