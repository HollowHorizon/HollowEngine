package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.FlyingAnimal
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.RenderPlayerEvent
import ru.hollowhorizon.hc.client.models.gltf.GltfTree
import ru.hollowhorizon.hc.client.models.gltf.ModelData
import ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType
import ru.hollowhorizon.hc.client.models.gltf.animations.GLTFAnimationPlayer
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode
import ru.hollowhorizon.hc.client.utils.SkinDownloader
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.memoize
import ru.hollowhorizon.hc.client.utils.rl

object PlayerRenderer {

    fun render(event: RenderPlayerEvent.Pre) {
        if(!event.entity.isAlive) return

        val stack = event.poseStack

        val capability = event.entity[AnimatedEntityCapability::class]

        if (capability.model == "%NO_MODEL%") return

        event.isCanceled = true
        val model = GltfManager.getOrCreate(capability.model.rl)

        stack.pushPose()

        preRender(event.entity, capability, model.animationPlayer, stack)

        val lerpBodyRot = Mth.rotLerp(event.partialTick, event.entity.yBodyRotO, event.entity.yBodyRot)
        stack.mulPose(Vector3f.YP.rotationDegrees(-lerpBodyRot))

        model.visuals = ::drawVisuals

        model.update(capability, event.entity.tickCount, event.partialTick)
        model.entityUpdate(event.entity, capability, event.partialTick)

        model.render(
            stack,
            ModelData(event.entity.offhandItem, event.entity.mainHandItem, null, event.entity),
            { texture: ResourceLocation ->
                val result = capability.textures[texture.path]?.let {
                    if (it.startsWith("skins/")) SkinDownloader.downloadSkin(it.substring(6))
                    else it.rl
                } ?: texture

                Minecraft.getInstance().textureManager.getTexture(result).id
            }.memoize(),
            event.packedLight,
            OverlayTexture.pack(0, if (event.entity.hurtTime > 0 || !event.entity.isAlive) 3 else 10)
        )
        stack.popPose()
    }

    fun drawVisuals(entity: LivingEntity, stack: PoseStack, node: GltfTree.Node, light: Int) {
        if ((node.name?.contains("left", ignoreCase = true) == true || node.name?.contains(
                "right",
                ignoreCase = true
            ) == true) &&
            node.name!!.contains("hand", ignoreCase = true) &&
            node.name!!.contains("item", ignoreCase = true)
        ) {
            val isLeft = node.name!!.contains("left", ignoreCase = true)
            val item = (if (isLeft) entity.getItemInHand(InteractionHand.OFF_HAND) else entity.getItemInHand(
                InteractionHand.MAIN_HAND
            )) ?: return

            stack.pushPose()
            stack.mulPose(Vector3f.XP.rotationDegrees(-90.0f))

            Minecraft.getInstance().itemRenderer.renderStatic(
                entity,
                item,
                if (isLeft) ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND else ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                isLeft,
                stack,
                Minecraft.getInstance().renderBuffers().bufferSource(),
                entity.level,
                light,
                OverlayTexture.NO_OVERLAY,
                0
            )

            stack.popPose()
        }
    }

    private fun preRender(
        entity: Player,
        capability: AnimatedEntityCapability,
        manager: GLTFAnimationPlayer,
        stack: PoseStack,
    ) {
        stack.mulPoseMatrix(capability.transform.matrix)
        stack.mulPose(Vector3f.YP.rotationDegrees(180f))
        updateAnimations(stack, entity, capability, manager)
    }

    private fun updateAnimations(
        stack: PoseStack,
        entity: LivingEntity,
        capability: AnimatedEntityCapability,
        manager: GLTFAnimationPlayer
    ) {
        let {
            when {
                entity.hurtTime > 0 -> {
                    val name = manager.typeToAnimationMap[AnimationType.HURT]?.name ?: return@let
                    if (capability.layers.any { it.animation == name }) return@let

                    capability.layers += AnimationLayer(
                        name,
                        LayerMode.ADD,
                        PlayMode.ONCE,
                        1.0f, fadeIn = 5
                    )
                }

                entity.swinging -> {
                    val name = manager.typeToAnimationMap[AnimationType.SWING]?.name ?: return@let
                    if (capability.layers.any { it.animation == name }) return@let

                    capability.layers += AnimationLayer(
                        name,
                        LayerMode.ADD,
                        PlayMode.ONCE,
                        1.0f, fadeIn = 5
                    )
                }

                !entity.isAlive -> {
                    val name = manager.typeToAnimationMap[AnimationType.DEATH]?.name ?: return@let
                    if (capability.layers.any { it.animation == name }) return@let

                    capability.layers += AnimationLayer(
                        name,
                        LayerMode.ADD,
                        PlayMode.LAST_FRAME,
                        1.0f, fadeIn = 5
                    )
                }
            }
        }
        manager.currentLoopAnimation = when {
            entity is FlyingAnimal && entity.isFlying -> AnimationType.FLY
            entity.isSleeping -> AnimationType.SLEEP
            entity.vehicle != null -> {
                stack.translate(0.0, 0.4, 0.0)
                AnimationType.SIT
            }
            entity.fallFlyingTicks > 4 -> AnimationType.FALL
            entity.animationSpeed > 0.1 -> {
                when {
                    entity.isVisuallySwimming -> AnimationType.SWIM
                    entity.isShiftKeyDown -> AnimationType.WALK_SNEAKED
                    entity.animationSpeed > 0.95f -> AnimationType.RUN
                    else -> AnimationType.WALK
                }
            }

            else -> AnimationType.IDLE
        }
    }
}