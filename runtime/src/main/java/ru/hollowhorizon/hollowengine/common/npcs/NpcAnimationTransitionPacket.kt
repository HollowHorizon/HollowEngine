package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.EntitySnapshotPacket
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.models.ServerModelAnimationMetadata
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import kotlin.math.ceil

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class NpcAnimationTransitionPacket(
    val entityId: Int,
    val from: String? = null,
    val to: String? = null,
    val duration: Float = 0.33f,
    val playMode: AnimationPlayMode = AnimationPlayMode.Once,
    val fadeIn: Float = 0.33f,
    val fadeOut: Float = 0.33f,
) : HollowPacket {
    override fun handle(player: Player) {
        if (player !is ServerPlayer) return
        val level = player.level()
        val entity = level.getEntity(entityId) ?: return

        NpcAnimationRuntime.apply(entity, from, to, playMode, duration, fadeIn, fadeOut)
    }
}

@SubscribeEvent
fun onNpcAnimationServerTick(event: TickEvent.Server) {
    NpcAnimationRuntime.tick(event.server)
}

object NpcAnimationRuntime {
    fun animationDuration(entity: Entity, animation: String): Float? {
        val model = GearyRuntimeState.componentsById(entity).values.filterIsInstance<Model>().firstOrNull()?.model
            ?: return null
        return ServerModelAnimationMetadata.animationDuration(model, animation)
    }

    fun apply(
        entity: Entity,
        from: String?,
        to: String?,
        playMode: AnimationPlayMode,
        duration: Float = DEFAULT_FADE_DURATION,
        fadeIn: Float = DEFAULT_FADE_DURATION,
        fadeOut: Float = DEFAULT_FADE_DURATION,
    ) {
        val animatorId = ComponentDescriptorRegistry.idFor(AnimatorComponent::class) ?: return
        val components = GearyRuntimeState.componentsById(entity)
        val current = components[animatorId] as? AnimatorComponent ?: AnimatorComponent()
        val withoutOld = from?.let { current.fadeOutClip(entity.level().gameTime, it, duration) } ?: current
        val model = components.values.filterIsInstance<Model>().firstOrNull()?.model
        val gameTime = entity.level().gameTime
        val updated = to
            ?.takeIf(String::isNotBlank)
            ?.let { animation ->
                withoutOld.withLayer(
                    ClipAnimationLayerSpec(
                        id = "npc:$animation",
                        animation = animation,
                        playMode = playMode,
                        fadeIn = fadeIn,
                        fadeOut = fadeOut,
                        removeOnEnd = playMode == AnimationPlayMode.Once,
                        removeAtGameTime = completionGameTime(gameTime, model, animation, playMode, fadeOut),
                    )
                )
            } ?: withoutOld

        components[animatorId] = updated
        val serverEntity = entity.takeIf { !it.level().isClientSide } ?: return
        EntitySnapshotPacket(
            serverEntity.id,
            snapshotOf(serverEntity),
        ).sendTrackingEntityAndSelf(serverEntity)
    }

    fun removeLayer(entity: Entity, layerId: String) {
        val animatorId = ComponentDescriptorRegistry.idFor(AnimatorComponent::class) ?: return
        val components = GearyRuntimeState.componentsById(entity)
        val current = components[animatorId] as? AnimatorComponent ?: return
        val updated = current.withoutLayer(layerId)
        if (updated == current) return

        components[animatorId] = updated
        val serverEntity = entity.takeIf { !it.level().isClientSide } ?: return
        EntitySnapshotPacket(
            serverEntity.id,
            snapshotOf(serverEntity),
        ).sendTrackingEntityAndSelf(serverEntity)
    }

    fun clear(entity: Entity) {
        val animatorId = ComponentDescriptorRegistry.idFor(AnimatorComponent::class) ?: return
        val components = GearyRuntimeState.componentsById(entity)
        val current = components[animatorId] as? AnimatorComponent ?: return
        val updated = current.copy(layers = current.layers.filterNot { it is ClipAnimationLayerSpec })
        if (updated == current) return

        components[animatorId] = updated
        val serverEntity = entity.takeIf { !it.level().isClientSide } ?: return
        EntitySnapshotPacket(
            serverEntity.id,
            snapshotOf(serverEntity),
        ).sendTrackingEntityAndSelf(serverEntity)
    }

    fun tick(server: MinecraftServer) {
        server.allLevels.forEach(::removeExpiredLayers)
    }

    private fun removeExpiredLayers(level: ServerLevel) {
        val animatorId = ComponentDescriptorRegistry.idFor(AnimatorComponent::class) ?: return
        val now = level.gameTime
        GearyRuntimeState.entitySnapshots(level).forEach { (entity, snapshot) ->
            val animator = snapshot.components.filterIsInstance<AnimatorComponent>().firstOrNull() ?: return@forEach
            val updated = animator.copy(
                layers = animator.layers.filterNot { layer ->
                    layer is ClipAnimationLayerSpec && layer.removeAtGameTime?.let { it <= now } == true
                }
            )
            if (updated == animator) return@forEach

            GearyRuntimeState.componentsById(entity)[animatorId] = updated
            EntitySnapshotPacket(
                entity.id,
                snapshotOf(entity),
            ).sendTrackingEntityAndSelf(entity)
        }
    }

    private fun AnimatorComponent.fadeOutClip(
        gameTime: Long,
        animation: String,
        duration: Float,
    ): AnimatorComponent {
        if (duration <= 0f) return withoutClip(animation)
        val durationTicks = duration.toDouble().secondsToTicksCeil()
        val removeAt = gameTime + durationTicks
        var changed = false
        val updatedLayers = layers.map { layer ->
            if (layer !is ClipAnimationLayerSpec || layer.animation != animation) return@map layer
            changed = true
            layer.copy(
                weight = AnimationExpression(layer.weight.source.fadeOutExpression(gameTime, durationTicks)),
                removeOnEnd = false,
                removeAtGameTime = removeAt,
            )
        }
        return if (changed) copy(layers = updatedLayers) else this
    }

    private fun completionGameTime(
        gameTime: Long,
        model: String?,
        animation: String,
        playMode: AnimationPlayMode,
        fadeOut: Float,
    ): Long? {
        if (playMode != AnimationPlayMode.Once || model == null) return null
        val duration = ServerModelAnimationMetadata.animationDuration(model, animation) ?: return null
        return gameTime + (duration + fadeOut.coerceAtLeast(0f)).toDouble().secondsToTicksCeil()
    }

    private fun String.fadeOutExpression(gameTime: Long, durationTicks: Long): String {
        val base = if (isBlank()) "1" else this
        return "($base) * clamp(1 - (game_time - $gameTime) / ${durationTicks.toFloat()}, 0, 1)"
    }

    private fun Double.secondsToTicksCeil(): Long =
        ceil(this * TICKS_PER_SECOND).toLong().coerceAtLeast(1L)

    private const val DEFAULT_FADE_DURATION = 0.33f
    private const val TICKS_PER_SECOND = 20.0
}
