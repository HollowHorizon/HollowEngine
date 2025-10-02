package ru.hollowhorizon.hollowengine.common.components.entity

import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.client.kool.addons.BooleanRenderer
import ru.hollowhorizon.hollowengine.client.kool.addons.ResourceLocationRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.ModelData
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hollowengine.client.models.internal.controller.AutoController
import ru.hollowhorizon.hollowengine.client.models.internal.controller.Controller
import ru.hollowhorizon.hollowengine.client.models.internal.controller.StateMachineBuilder
import ru.hollowhorizon.hollowengine.client.models.internal.controller.animationController
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.system.Cardinal
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.LivingEntityQuery
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext
import ru.hollowhorizon.hollowengine.common.utils.mutableLazy
import ru.hollowhorizon.hollowengine.common.utils.rl

@ComponentMeta("hollowengine:model_renderer")
class ModelComponent : Component<LivingEntity>() {
    var model: String by property { "hollowengine:models/entity/player_model.gltf" }
        .renderer { ResourceLocationRenderer(HollowModelManager.allModels.map { it.toString() }, "Путь к модели") }
        .copyOnDeath()
        .onChange { old, new ->
            if (!isLogicalClient) return@onChange
            internalModel = HollowModelManager.getOrCreate(new.rl)
        }

    internal var internalModel: AnimatedModel by mutableLazy { HollowModelManager.getOrCreate(model.rl) }

}

@ComponentMeta("hollowengine:animator")
class AnimatorComponent : Component<LivingEntity>() {
    var controller: Controller by property {
        animationController {
            automatic()
            head("Head")
        }
    }.copyOnDeath()
        .onChange { old, new ->
            if(!isPhysicalClient) return@onChange
            Minecraft.getInstance().coroutineScope.launch {
                new.layers.find { it.name == Controller.AUTOMATIC_LAYER }?.let {
                    val model = HollowModelManager.getOrCreate(model.model.rl)
                    val stateMachine = AutoController.create(StateMachineBuilder(), AnimationType.load(model))
                    it.stateMachine = stateMachine.build()
                }

                new.transferFrom(old)
            }
        }

    override fun onAttach() {
        if(!isPhysicalClient) return
        controller.layers.find { it.name == Controller.AUTOMATIC_LAYER }?.let {
            val model = HollowModelManager.getOrCreate(model.model.rl)
            val stateMachine = AutoController.create(StateMachineBuilder(), AnimationType.load(model))
            it.stateMachine = stateMachine.build()
        }
    }

    val molangContext by lazy {
        MolangContext(LivingEntityQuery(owner))
    }

    val model: ModelComponent by requires<ModelComponent>()
}


@Init
fun loadComponents() {

    Cardinal.on<RenderEntityEvent.Pre, ModelComponent> { model ->
        poseStack.pushPose()
        if (model.internalModel.model.isBlockBench) poseStack.mulPose(Quaternionf().rotateY(180f * Mth.DEG_TO_RAD))
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

    Cardinal.on<RenderEntityEvent.Pre, AnimatorComponent>(100) { animator ->
        val controller = animator.controller
        controller.uploadAnimations(animator.model.internalModel.animations)
        animator.model.internalModel.update(
            controller, animator.molangContext,
            (entity.tickCount + partialTicks) / 20f
        )
    }
}