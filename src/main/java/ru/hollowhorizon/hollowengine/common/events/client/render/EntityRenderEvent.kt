package ru.hollowhorizon.hollowengine.common.events.client.render

import com.google.common.collect.ImmutableMap
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.SkullModelBase
import net.minecraft.client.model.geom.EntityModelSet
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.SkullBlock
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.ComponentDispatcherEvent
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

class RegisterEntityLayersDefinitions(private val layerDefinitions: Map<ModelLayerLocation, () -> LayerDefinition>) :
    ClientEvent {
    fun registerLayerDefinition(location: ModelLayerLocation, layerDefinition: () -> LayerDefinition) {
        (layerDefinitions as MutableMap)[location] = layerDefinition
    }
}

class AddEntityRendererLayers(
    val renderers: MutableMap<EntityType<*>, EntityRenderer<*>>,
    val skinMap: MutableMap<String, EntityRenderer<out Player>>,
    val context: EntityRendererProvider.Context,
) : ClientEvent {
    val skins = this.skinMap.keys

    fun <R : LivingEntityRenderer<out Player, out EntityModel<out Player>>> getSkin(skin: String): R =
        JavaHacks.forceCast(this.skinMap[skin])

    fun <T : LivingEntity, R : LivingEntityRenderer<T, out EntityModel<T>>> getRenderer(type: EntityType<out T>): R =
        JavaHacks.forceCast(this.renderers[type])

    val entityModels = this.context.modelSet
}

class CreateEntitySkullModels(
    private val builder: ImmutableMap.Builder<SkullBlock.Type, SkullModelBase>,
    val entityModelSet: EntityModelSet,
) : ClientEvent {
    fun registerSkullModel(type: SkullBlock.Type, model: SkullModelBase) {
        builder.put(type, model)
    }
}

open class RenderEntityEvent(
    val entity: Entity,
    val entityYaw: Float,
    val partialTicks: Float,
    val poseStack: PoseStack,
    val buffer: MultiBufferSource,
    val packedLight: Int,
) : ComponentDispatcherEvent<Entity>, ClientEvent {
    override val owner: ComponentDispatcher
        get() = entity as ComponentDispatcher

    class Pre(
        entity: Entity,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) : RenderEntityEvent(entity, entityYaw, partialTicks, poseStack, buffer, packedLight), Cancelable {
        override var isCanceled = false
    }

    class Post(
        entity: Entity,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) : RenderEntityEvent(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)

}

class RenderPlayerEvent(
    val player: AbstractClientPlayer,
    val entityYaw: Float,
    val partialTicks: Float,
    val poseStack: PoseStack,
    val buffer: MultiBufferSource,
    val packedLight: Int,
) : ComponentDispatcherEvent<AbstractClientPlayer>, ClientEvent, Cancelable {
    override val owner: ComponentDispatcher
        get() = player as ComponentDispatcher
    override var isCanceled = false
}