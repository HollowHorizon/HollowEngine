package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry
import ru.hollowhorizon.hollowengine.common.attachments.components.*
import ru.hollowhorizon.hollowengine.common.models.*
import ru.hollowhorizon.hollowengine.common.models.ServerModelAnimationMetadata
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
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
        val model = AttachmentRegistry.componentsById(entity).values.filterIsInstance<Model>().firstOrNull()?.model
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
        val animationsId = ComponentDescriptorRegistry.idFor(AnimationsComponent::class) ?: return
        val components = AttachmentRegistry.componentsById(entity)
        val current = components[animationsId] as? AnimationsComponent ?: AnimationsComponent()
        val withoutOld = from?.let { current.fadeOutClip(entity.level().gameTime, it, duration) } ?: current
        val model = components.values.filterIsInstance<Model>().firstOrNull()?.model
        val gameTime = entity.level().gameTime
        val updated = to
            ?.takeIf(String::isNotBlank)
            ?.let { animation ->
                withoutOld.withClip(
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

        components[animationsId] = updated
    }

    fun removeLayer(entity: Entity, layerId: String) {
        val animationsId = ComponentDescriptorRegistry.idFor(AnimationsComponent::class) ?: return
        val components = AttachmentRegistry.componentsById(entity)
        val current = components[animationsId] as? AnimationsComponent ?: return
        val updated = current.withoutLayer(layerId)
        if (updated == current) return

        components[animationsId] = updated
    }

    fun clear(entity: Entity) {
        val animationsId = ComponentDescriptorRegistry.idFor(AnimationsComponent::class) ?: return
        val components = AttachmentRegistry.componentsById(entity)
        val current = components[animationsId] as? AnimationsComponent ?: return
        if (current.clips.isEmpty()) return

        components[animationsId] = AnimationsComponent()
    }

    fun tick(server: MinecraftServer) {
        server.allLevels.forEach(::removeExpiredLayers)
    }

    private fun removeExpiredLayers(level: ServerLevel) {
        val animationsId = ComponentDescriptorRegistry.idFor(AnimationsComponent::class) ?: return
        val now = level.gameTime
        AttachmentRegistry.entitySnapshots(level).forEach { (entity, snapshot) ->
            val animations = snapshot.components.filterIsInstance<AnimationsComponent>().firstOrNull() ?: return@forEach
            val updated = animations.copy(
                clips = animations.clips.filterNot { clip -> clip.removeAtGameTime?.let { it <= now } == true }
            )
            if (updated == animations) return@forEach

            AttachmentRegistry.componentsById(entity)[animationsId] = updated
        }
    }

    private fun AnimationsComponent.fadeOutClip(
        gameTime: Long,
        animation: String,
        duration: Float,
    ): AnimationsComponent {
        if (duration <= 0f) return withoutClip(animation)
        val durationTicks = duration.toDouble().secondsToTicksCeil()
        val removeAt = gameTime + durationTicks
        var changed = false
        val updatedClips = clips.map { clip ->
            if (clip.animation != animation) return@map clip
            changed = true
            clip.copy(
                weight = AnimationExpression(clip.weight.source.fadeOutExpression(gameTime, durationTicks)),
                removeOnEnd = false,
                removeAtGameTime = removeAt,
            )
        }
        return if (changed) copy(clips = updatedClips) else this
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
