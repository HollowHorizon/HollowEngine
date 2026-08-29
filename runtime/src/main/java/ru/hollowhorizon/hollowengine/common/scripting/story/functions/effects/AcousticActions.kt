package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.attachments.binding.ROOT_COMPONENT_ID
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticEntityAnchor
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticFade
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticFloatUpdate
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticLoop
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayOptions
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayRequest
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticPlayback
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticSource
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticStopRequest
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticUpdateOptions
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.AcousticUpdateRequest
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.HollowAcoustic
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.HollowModelAcousticAnchor
import ru.hollowhorizon.hollowengine.common.integrations.acoustic.requireNonNegativeFinite
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.UUID

@DslMarker
annotation class AcousticDsl

@AcousticDsl
open class AcousticSourceBuilder internal constructor() {
    internal var source: AcousticSource? = null

    fun listener() {
        source = AcousticSource.Listener
    }

    fun at(position: Vec3) {
        source = AcousticSource.Position(position)
    }

    fun at(x: Double, y: Double, z: Double) = at(Vec3(x, y, z))

    fun follow(entity: Entity, anchor: AcousticEntityAnchor = AcousticEntityAnchor.CENTER) {
        source = AcousticSource.EntityAnchor(entity, anchor)
    }

    fun attachTo(entity: Entity, attachment: EntityAttachment, index: Int = 0) {
        source = AcousticSource.VanillaAttachment(entity, attachment, index)
    }

    fun attachTo(attachmentId: ResourceLocation) {
        source = AcousticSource.NamedAttachment(attachmentId)
    }

    fun attachTo(attachmentId: String) = attachTo(attachmentId.rl)

    fun attachToModel(entity: Entity, nodeId: UUID = ROOT_COMPONENT_ID) {
        source = AcousticSource.HollowModel(entity, nodeId)
    }

    fun attachToBone(entity: Entity, boneName: String, nodeId: UUID = ROOT_COMPONENT_ID) {
        source = AcousticSource.HollowModel(
            entity = entity,
            nodeId = nodeId,
            anchor = HollowModelAcousticAnchor.BoneName(boneName),
        )
    }

    fun attachToBonePath(entity: Entity, bonePath: String, nodeId: UUID = ROOT_COMPONENT_ID) {
        attachToBonePath(entity, HollowModelAcousticAnchor.BonePath(bonePath).segments, nodeId)
    }

    fun attachToBonePath(entity: Entity, bonePath: List<String>, nodeId: UUID = ROOT_COMPONENT_ID) {
        source = AcousticSource.HollowModel(
            entity = entity,
            nodeId = nodeId,
            anchor = HollowModelAcousticAnchor.BonePath(bonePath),
        )
    }
}

@AcousticDsl
class AcousticPlayBuilder internal constructor() : AcousticSourceBuilder() {
    private var loop: AcousticLoop? = null
    private var startOffsetSeconds: Float? = null
    private var endOffsetSeconds: Float? = null
    private var volume: Float? = null
    private var pitch: Float? = null
    private var fadeIn: AcousticFade? = null
    private var fadeOut: AcousticFade? = null
    private var exclusive: Boolean? = null
    private var priority: Int? = null
    private var priorityFadeOutSeconds: Float? = null
    private var range: Float? = null
    private var sourceTimeoutSeconds: Float? = null
    private var instanceId: String? = null
    private var condition: ((ServerPlayer) -> Boolean)? = null

    fun loop() {
        loop = AcousticLoop.Infinite
    }

    fun loop(count: Int) {
        loop = AcousticLoop.Count(count)
    }

    fun once() = loop(1)

    fun start(seconds: Float) {
        requireNonNegativeFinite(seconds, "Acoustic start offset")
        startOffsetSeconds = seconds
    }

    fun end(seconds: Float) {
        require(seconds.isFinite()) { "Acoustic end offset must be finite" }
        endOffsetSeconds = seconds
    }

    fun volume(value: Float) {
        require(value.isFinite()) { "Acoustic volume must be finite" }
        volume = value
    }

    fun pitch(value: Float) {
        require(value.isFinite()) { "Acoustic pitch must be finite" }
        pitch = value
    }

    fun fadeIn(seconds: Float, repeatOnLoop: Boolean = false) {
        fadeIn = AcousticFade(seconds, repeatOnLoop)
    }

    fun fadeOut(seconds: Float, repeatOnLoop: Boolean = false) {
        fadeOut = AcousticFade(seconds, repeatOnLoop)
    }

    fun exclusive(value: Boolean = true) {
        exclusive = value
    }

    fun priority(value: Int) {
        priority = value
    }

    fun priorityFadeOut(seconds: Float) {
        requireNonNegativeFinite(seconds, "Acoustic priority fade-out duration")
        priorityFadeOutSeconds = seconds
    }

    fun range(value: Float) {
        require(value.isFinite() && value > 0f) { "Acoustic range must be a finite positive value" }
        range = value
    }

    fun sourceTimeout(seconds: Float) {
        requireNonNegativeFinite(seconds, "Acoustic source timeout")
        sourceTimeoutSeconds = seconds
    }

    fun withId(value: String) {
        require(value.isNotBlank()) { "Acoustic playback ID cannot be blank" }
        instanceId = value
    }

    fun condition(predicate: (ServerPlayer) -> Boolean) {
        condition = predicate
    }

    internal fun build(): AcousticPlayOptions = AcousticPlayOptions(
        loop = loop,
        startOffsetSeconds = startOffsetSeconds,
        endOffsetSeconds = endOffsetSeconds,
        volume = volume,
        pitch = pitch,
        fadeIn = fadeIn,
        fadeOut = fadeOut,
        exclusive = exclusive,
        priority = priority,
        priorityFadeOutSeconds = priorityFadeOutSeconds,
        source = source,
        range = range,
        sourceTimeoutSeconds = sourceTimeoutSeconds,
        instanceId = instanceId,
        condition = condition,
    )
}

@AcousticDsl
class AcousticUpdateBuilder internal constructor() : AcousticSourceBuilder() {
    private var loop: AcousticLoop? = null
    private var startOffsetSeconds: Float? = null
    private var endOffsetSeconds: Float? = null
    private var volume: AcousticFloatUpdate? = null
    private var pitch: AcousticFloatUpdate? = null
    private var fadeIn: AcousticFade? = null
    private var fadeOut: AcousticFade? = null
    private var exclusive: Boolean? = null
    private var priority: Int? = null
    private var priorityFadeOutSeconds: Float? = null
    private var range: Float? = null

    fun loop() {
        loop = AcousticLoop.Infinite
    }

    fun loop(count: Int) {
        loop = AcousticLoop.Count(count)
    }

    fun once() = loop(1)

    fun start(seconds: Float) {
        requireNonNegativeFinite(seconds, "Acoustic start offset")
        startOffsetSeconds = seconds
    }

    fun end(seconds: Float) {
        require(seconds.isFinite()) { "Acoustic end offset must be finite" }
        endOffsetSeconds = seconds
    }

    fun volume(value: Float, transitionSeconds: Float = 0f) {
        volume = AcousticFloatUpdate(value, transitionSeconds)
    }

    fun pitch(value: Float, transitionSeconds: Float = 0f) {
        pitch = AcousticFloatUpdate(value, transitionSeconds)
    }

    fun fadeIn(seconds: Float, repeatOnLoop: Boolean = false) {
        fadeIn = AcousticFade(seconds, repeatOnLoop)
    }

    fun fadeOut(seconds: Float, repeatOnLoop: Boolean = false) {
        fadeOut = AcousticFade(seconds, repeatOnLoop)
    }

    fun exclusive(value: Boolean = true) {
        exclusive = value
    }

    fun priority(value: Int) {
        priority = value
    }

    fun priorityFadeOut(seconds: Float) {
        requireNonNegativeFinite(seconds, "Acoustic priority fade-out duration")
        priorityFadeOutSeconds = seconds
    }

    fun range(value: Float) {
        require(value.isFinite() && value > 0f) { "Acoustic range must be a finite positive value" }
        range = value
    }

    internal fun build(): AcousticUpdateOptions = AcousticUpdateOptions(
        loop = loop,
        startOffsetSeconds = startOffsetSeconds,
        endOffsetSeconds = endOffsetSeconds,
        volume = volume,
        pitch = pitch,
        fadeIn = fadeIn,
        fadeOut = fadeOut,
        exclusive = exclusive,
        priority = priority,
        priorityFadeOutSeconds = priorityFadeOutSeconds,
        source = source,
        range = range,
    )
}

fun ServerPlayer.playAcoustic(
    sound: String,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback = listOf(this).playAcoustic(sound.rl, configure)

fun ServerPlayer.playAcoustic(
    sound: ResourceLocation,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback = listOf(this).playAcoustic(sound, configure)

fun Collection<ServerPlayer>.playAcoustic(
    sound: String,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback = playAcoustic(sound.rl, configure)

fun Collection<ServerPlayer>.playAcoustic(
    sound: ResourceLocation,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback {
    require(isNotEmpty()) { "Acoustic playback needs at least one receiving player" }
    val players = distinctBy(ServerPlayer::getUUID)
    val options = AcousticPlayBuilder().apply(configure).build()
    return HollowAcoustic.play(AcousticPlayRequest(sound, players, options))
}

fun ServerLevel.playAcoustic(
    sound: String,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback = players().playAcoustic(sound, configure)

fun ServerLevel.playAcoustic(
    sound: ResourceLocation,
    configure: AcousticPlayBuilder.() -> Unit = {},
): AcousticPlayback = players().playAcoustic(sound, configure)

fun AcousticPlayback.updateAcoustic(
    player: ServerPlayer,
    configure: AcousticUpdateBuilder.() -> Unit,
) = updateAcoustic(listOf(player), configure)

fun AcousticPlayback.updateAcoustic(
    players: Collection<ServerPlayer>,
    configure: AcousticUpdateBuilder.() -> Unit,
) {
    require(players.isNotEmpty()) { "Acoustic update needs at least one receiving player" }
    val options = AcousticUpdateBuilder().apply(configure).build()
    HollowAcoustic.update(AcousticUpdateRequest(this, players.distinctBy(ServerPlayer::getUUID), options))
}

fun AcousticPlayback.stopAcoustic(player: ServerPlayer, fadeOutSeconds: Float = 0f) =
    stopAcoustic(listOf(player), fadeOutSeconds)

fun AcousticPlayback.stopAcoustic(players: Collection<ServerPlayer>, fadeOutSeconds: Float = 0f) {
    require(players.isNotEmpty()) { "Acoustic stop needs at least one receiving player" }
    requireNonNegativeFinite(fadeOutSeconds, "Acoustic stop fade-out duration")
    HollowAcoustic.stop(
        AcousticStopRequest(this, players.distinctBy(ServerPlayer::getUUID), fadeOutSeconds),
    )
}
