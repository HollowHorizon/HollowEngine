package ru.hollowhorizon.hollowengine.addons.acoustic

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.bmp.acoustic.Acoustic
import org.bmp.acoustic.AcousticSourceBuilder
import org.bmp.acoustic.SoundBuilder
import org.bmp.acoustic.UpdateBuilder
import org.bmp.acoustic.source.AcousticEntityAnchor as TargetEntityAnchor
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticEntityAnchor
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticIntegration
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticLoop
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayOptions
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayRequest
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayback
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticSource
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticStopRequest
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticUpdateOptions
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticUpdateRequest

internal class AcousticIntegrationAdapter : AcousticIntegration {
    override fun play(request: AcousticPlayRequest): AcousticPlayback {
        require(request.players.isNotEmpty()) { "Acoustic playback needs at least one receiving player" }
        val players = request.players.distinctBy(ServerPlayer::getUUID)
        val condition = request.options.condition
        val recipients = condition?.let { predicate -> players.filter(predicate) } ?: players
        val options = if (condition == null) request.options else request.options.copy(condition = null)
        val builder = Acoustic.soundManager.play(request.sound, recipients)
        builder.applyOptions(options)
        val modelTarget = options.source?.hollowModelTargetOrNull()
        if (modelTarget != null) {
            SetHollowModelAcousticTargetPacket(builder.instanceId, modelTarget).send(recipients)
        }
        val playback = AcousticPlayback(builder.execute())
        if (options.source != null && modelTarget == null) {
            SetHollowModelAcousticTargetPacket(playback.instanceId, null).send(recipients)
        }
        return playback
    }

    override fun update(request: AcousticUpdateRequest) {
        require(request.players.isNotEmpty()) { "Acoustic update needs at least one receiving player" }
        val players = request.players.distinctBy(ServerPlayer::getUUID)
        val modelTarget = request.options.source?.hollowModelTargetOrNull()
        if (modelTarget != null) {
            SetHollowModelAcousticTargetPacket(request.playback.instanceId, modelTarget).send(players)
        }
        Acoustic.soundManager.update(request.playback.instanceId, players)
            .applyOptions(request.options)
            .execute()
        if (request.options.source != null && modelTarget == null) {
            SetHollowModelAcousticTargetPacket(request.playback.instanceId, null).send(players)
        }
    }

    override fun stop(request: AcousticStopRequest) {
        require(request.players.isNotEmpty()) { "Acoustic stop needs at least one receiving player" }
        Acoustic.soundManager.stop(
            request.playback.instanceId,
            request.players.distinctBy(ServerPlayer::getUUID),
        )
            .fadeOut(request.fadeOutSeconds)
            .execute()
    }
}

internal fun SoundBuilder.applyOptions(
    options: AcousticPlayOptions,
): SoundBuilder = apply {
    applyLoop(options.loop)
    options.startOffsetSeconds?.let(::start)
    options.endOffsetSeconds?.let(::end)
    options.volume?.let(::volume)
    options.pitch?.let(::pitch)
    options.fadeIn?.let { fade -> fadeIn(fade.seconds, fade.repeatOnLoop) }
    options.fadeOut?.let { fade -> fadeOut(fade.seconds, fade.repeatOnLoop) }
    options.exclusive?.let(::exclusive)
    options.priority?.let(::priority)
    options.priorityFadeOutSeconds?.let(::priorityFadeOut)
    options.source?.let(::applySource)
    options.range?.let(::range)
    options.sourceTimeoutSeconds?.let(::sourceTimeout)
    options.instanceId?.let(::withId)
    options.condition?.let { predicate -> condition { player -> predicate(player) } }
}

internal fun UpdateBuilder.applyOptions(
    options: AcousticUpdateOptions,
): UpdateBuilder = apply {
    applyLoop(options.loop)
    options.startOffsetSeconds?.let(::start)
    options.endOffsetSeconds?.let(::end)
    options.volume?.let { update -> volume(update.value, update.transitionSeconds) }
    options.pitch?.let { update -> pitch(update.value, update.transitionSeconds) }
    options.fadeIn?.let { fade -> fadeIn(fade.seconds, fade.repeatOnLoop) }
    options.fadeOut?.let { fade -> fadeOut(fade.seconds, fade.repeatOnLoop) }
    options.exclusive?.let(::exclusive)
    options.priority?.let(::priority)
    options.priorityFadeOutSeconds?.let(::priorityFadeOut)
    options.source?.let(::applySource)
    options.range?.let(::range)
}

private fun SoundBuilder.applyLoop(loop: AcousticLoop?) {
    when (loop) {
        null -> Unit
        AcousticLoop.Infinite -> loop()
        is AcousticLoop.Count -> loop(loop.count)
    }
}

private fun UpdateBuilder.applyLoop(loop: AcousticLoop?) {
    when (loop) {
        null -> Unit
        AcousticLoop.Infinite -> loop()
        is AcousticLoop.Count -> loop(loop.count)
    }
}

private fun AcousticSourceBuilder<*>.applySource(source: AcousticSource) {
    when (source) {
        AcousticSource.Listener -> listener()
        is AcousticSource.Position -> at(source.position)
        is AcousticSource.EntityAnchor -> {
            source.entity.requireLogicalServerSource()
            follow(source.entity, source.anchor.toTarget())
        }
        is AcousticSource.VanillaAttachment -> {
            source.entity.requireLogicalServerSource()
            attachTo(source.entity, source.attachment, source.index)
        }
        is AcousticSource.NamedAttachment -> attachTo(source.attachmentId)
        is AcousticSource.HollowModel -> {
            source.entity.requireLogicalServerSource()
            val target = HollowModelAcousticTarget.from(source)
            attachTo(target.attachmentId)
        }
    }
}

private fun AcousticSource.hollowModelTargetOrNull(): HollowModelAcousticTarget? =
    (this as? AcousticSource.HollowModel)?.let(HollowModelAcousticTarget::from)

private fun Entity.requireLogicalServerSource() {
    require(!level().isClientSide) {
        "An Acoustic entity source must be created from the logical server entity"
    }
}

private fun AcousticEntityAnchor.toTarget(): TargetEntityAnchor = when (this) {
    AcousticEntityAnchor.FEET -> TargetEntityAnchor.FEET
    AcousticEntityAnchor.CENTER -> TargetEntityAnchor.CENTER
    AcousticEntityAnchor.EYES -> TargetEntityAnchor.EYES
}
