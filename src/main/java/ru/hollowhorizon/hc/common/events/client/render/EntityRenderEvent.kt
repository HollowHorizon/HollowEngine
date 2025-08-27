package ru.hollowhorizon.hc.common.events.client.render

import com.google.common.collect.ImmutableMap
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.SkullModelBase
import net.minecraft.client.model.geom.EntityModelSet
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.SkullBlock
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.common.events.Event

class RegisterEntityLayersDefinitions(private val layerDefinitions: Map<ModelLayerLocation, () -> LayerDefinition>): Event {
    fun registerLayerDefinition(location: ModelLayerLocation, layerDefinition: () -> LayerDefinition) {
        (layerDefinitions as MutableMap)[location] = layerDefinition
    }
}

class AddEntityRendererLayers(
    val renderers: MutableMap<EntityType<*>, EntityRenderer<*>>,
    val skinMap: MutableMap<String, EntityRenderer<out Player>>,
    val context: EntityRendererProvider.Context
): Event {
    val skins = this.skinMap.keys

    fun <R: LivingEntityRenderer<out Player, out EntityModel<out Player>>> getSkin(skin: String): R =
        JavaHacks.forceCast(this.skinMap[skin])

    fun <T: LivingEntity, R: LivingEntityRenderer<T, out EntityModel<T>>> getRenderer(type: EntityType<out T>): R =
        JavaHacks.forceCast(this.renderers[type])

    val entityModels = this.context.modelSet
}

class CreateEntitySkullModels(
    private val builder: ImmutableMap.Builder<SkullBlock.Type, SkullModelBase>,
    val entityModelSet: EntityModelSet
): Event {
    fun registerSkullModel(type: SkullBlock.Type, model: SkullModelBase) {
        builder.put(type, model)
    }
}

class RenderPlayerEvent(
    val player: AbstractClientPlayer,
    val entityYaw: Float,
    val partialTicks: Float,
    val poseStack: PoseStack,
    val buffer: MultiBufferSource,
    val packedLight: Int
) : Event, Cancelable {
    override var isCanceled = false
}