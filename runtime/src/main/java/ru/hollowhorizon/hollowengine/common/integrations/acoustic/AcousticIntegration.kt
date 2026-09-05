package ru.hollowhorizon.hollowengine.common.integrations.acoustic

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonManager
import ru.hollowhorizon.hollowengine.common.attachments.binding.ROOT_COMPONENT_ID
import java.util.UUID

/**
 * Stable HollowEngine-facing contract implemented by the optional Acoustic addon.
 *
 * No Acoustic class crosses this boundary. Scripts therefore remain loadable when the mod or addon
 * is absent, and a reloaded addon cannot leave target-mod objects in a script classloader.
 */
interface AcousticIntegration {
    fun play(request: AcousticPlayRequest): AcousticPlayback

    fun update(request: AcousticUpdateRequest)

    fun stop(request: AcousticStopRequest)
}

data class AcousticPlayback(val instanceId: String) {
    init {
        require(instanceId.isNotBlank()) { "Acoustic playback ID cannot be blank" }
    }
}

data class AcousticPlayRequest(
    val sound: ResourceLocation,
    val players: List<ServerPlayer>,
    val options: AcousticPlayOptions = AcousticPlayOptions(),
)

data class AcousticUpdateRequest(
    val playback: AcousticPlayback,
    val players: List<ServerPlayer>,
    val options: AcousticUpdateOptions,
)

data class AcousticStopRequest(
    val playback: AcousticPlayback,
    val players: List<ServerPlayer>,
    val fadeOutSeconds: Float = 0f,
) {
    init {
        requireNonNegativeFinite(fadeOutSeconds, "Acoustic stop fade-out duration")
    }
}

data class AcousticPlayOptions(
    val loop: AcousticLoop? = null,
    val startOffsetSeconds: Float? = null,
    val endOffsetSeconds: Float? = null,
    val volume: Float? = null,
    val pitch: Float? = null,
    val fadeIn: AcousticFade? = null,
    val fadeOut: AcousticFade? = null,
    val exclusive: Boolean? = null,
    val priority: Int? = null,
    val priorityFadeOutSeconds: Float? = null,
    val source: AcousticSource? = null,
    val range: Float? = null,
    val sourceTimeoutSeconds: Float? = null,
    val instanceId: String? = null,
    val condition: ((ServerPlayer) -> Boolean)? = null,
) {
    init {
        validateOffsets(startOffsetSeconds, endOffsetSeconds)
        volume?.let { value -> require(value.isFinite()) { "Acoustic volume must be finite" } }
        pitch?.let { value -> require(value.isFinite()) { "Acoustic pitch must be finite" } }
        priorityFadeOutSeconds?.let { requireNonNegativeFinite(it, "Acoustic priority fade-out duration") }
        range?.let { requirePositiveFinite(it, "Acoustic range") }
        sourceTimeoutSeconds?.let { requireNonNegativeFinite(it, "Acoustic source timeout") }
        instanceId?.let { require(it.isNotBlank()) { "Acoustic playback ID cannot be blank" } }
    }
}

data class AcousticUpdateOptions(
    val loop: AcousticLoop? = null,
    val startOffsetSeconds: Float? = null,
    val endOffsetSeconds: Float? = null,
    val volume: AcousticFloatUpdate? = null,
    val pitch: AcousticFloatUpdate? = null,
    val fadeIn: AcousticFade? = null,
    val fadeOut: AcousticFade? = null,
    val exclusive: Boolean? = null,
    val priority: Int? = null,
    val priorityFadeOutSeconds: Float? = null,
    val source: AcousticSource? = null,
    val range: Float? = null,
) {
    init {
        validateOffsets(startOffsetSeconds, endOffsetSeconds)
        priorityFadeOutSeconds?.let { requireNonNegativeFinite(it, "Acoustic priority fade-out duration") }
        range?.let { requirePositiveFinite(it, "Acoustic range") }
    }
}

sealed interface AcousticLoop {
    data object Infinite : AcousticLoop

    data class Count(val count: Int) : AcousticLoop {
        init {
            require(count > 0) { "Acoustic loop count must be positive" }
        }
    }
}

data class AcousticFade(
    val seconds: Float,
    val repeatOnLoop: Boolean = false,
) {
    init {
        requireNonNegativeFinite(seconds, "Acoustic fade duration")
    }
}

data class AcousticFloatUpdate(
    val value: Float,
    val transitionSeconds: Float = 0f,
) {
    init {
        require(value.isFinite()) { "Acoustic value must be finite" }
        requireNonNegativeFinite(transitionSeconds, "Acoustic transition duration")
    }
}

sealed interface AcousticSource {
    data object Listener : AcousticSource

    data class Position(val position: Vec3) : AcousticSource {
        init {
            require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) {
                "Acoustic source position must be finite"
            }
        }
    }

    data class EntityAnchor(
        val entity: Entity,
        val anchor: AcousticEntityAnchor = AcousticEntityAnchor.CENTER,
    ) : AcousticSource

    data class VanillaAttachment(
        val entity: Entity,
        val attachment: EntityAttachment,
        val index: Int = 0,
    ) : AcousticSource {
        init {
            require(index >= 0) { "Entity attachment index cannot be negative" }
        }
    }

    /** A named attachment published through Acoustic's client attachment API. */
    data class NamedAttachment(val attachmentId: ResourceLocation) : AcousticSource

    /** A position evaluated from the posed HollowEngine model rather than the host entity origin. */
    data class HollowModel(
        val entity: Entity,
        val nodeId: UUID = ROOT_COMPONENT_ID,
        val anchor: HollowModelAcousticAnchor = HollowModelAcousticAnchor.Root,
    ) : AcousticSource
}

enum class AcousticEntityAnchor {
    FEET,
    CENTER,
    EYES,
}

sealed interface HollowModelAcousticAnchor {
    data object Root : HollowModelAcousticAnchor

    /** Resolves only when exactly one runtime node has this name. */
    data class BoneName(val name: String) : HollowModelAcousticAnchor {
        init {
            require(name.isNotBlank()) { "Bone name cannot be blank" }
        }
    }

    /** Resolves an exact slash-separated path through the runtime node hierarchy. */
    data class BonePath(val segments: List<String>) : HollowModelAcousticAnchor {
        init {
            require(segments.isNotEmpty()) { "Bone path cannot be empty" }
            require(segments.none(String::isBlank)) { "Bone path cannot contain blank segments" }
        }

        constructor(path: String) : this(parseBonePath(path))
    }
}

object HollowAcoustic {
    val isAvailable: Boolean
        get() = HollowAddonManager.find<AcousticIntegration>() != null

    fun play(request: AcousticPlayRequest): AcousticPlayback = integration().play(request)

    fun update(request: AcousticUpdateRequest) = integration().update(request)

    fun stop(request: AcousticStopRequest) = integration().stop(request)

    private fun integration(): AcousticIntegration = HollowAddonManager.find()
        ?: error(
            "Acoustic integration is unavailable. Install and enable the HollowEngine Acoustic addon " +
                "and the Acoustic mod on both the server and the receiving clients.",
        )
}

internal fun requireNonNegativeFinite(value: Float, name: String) {
    require(value.isFinite() && value >= 0f) { "$name must be a finite non-negative value" }
}

private fun requirePositiveFinite(value: Float, name: String) {
    require(value.isFinite() && value > 0f) { "$name must be a finite positive value" }
}

private fun validateOffsets(startOffsetSeconds: Float?, endOffsetSeconds: Float?) {
    startOffsetSeconds?.let { requireNonNegativeFinite(it, "Acoustic start offset") }
    endOffsetSeconds?.let { end ->
        require(end.isFinite()) { "Acoustic end offset must be finite" }
        require(end < 0f || end > (startOffsetSeconds ?: 0f)) {
            "Acoustic end offset must be greater than the start offset or negative for EOF"
        }
    }
}

private fun parseBonePath(path: String): List<String> {
    val normalized = path.replace('\\', '/').trim('/')
    require(normalized.isNotBlank()) { "Bone path cannot be empty" }
    return normalized.split('/').also { segments ->
        require(segments.none(String::isBlank)) { "Bone path cannot contain blank segments" }
    }
}
