package ru.hollowhorizon.hollowengine.common.geary.anchor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.geary.components.EditorHidden
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.UUID

private val EMPTY_UUID: UUID = UUID(0L, 0L)

@JvmInline
value class StableKey(val value: UUID) {
    companion object {
        fun random(): StableKey = StableKey(UUID.randomUUID())
    }
}

@Registerable
@Syncable
@EditorHidden
@Serializable
@SerialName("hollowengine:stable_key")
data class StableKeyComponent(
    val value: @Serializable(ForUuid::class) UUID = EMPTY_UUID,
) {
    val stableKey: StableKey get() = StableKey(value)
    val isInitialized: Boolean get() = value != EMPTY_UUID
}

@Serializable
sealed interface AnchorComponent

@Registerable
@Syncable
@EditorHidden
@Serializable
@SerialName("hollowengine:anchor/entity")
data class EntityAnchor(
    val hostUuid: @Serializable(ForUuid::class) UUID = EMPTY_UUID,
    val primary: Boolean = false,
) : AnchorComponent

@Registerable
@Syncable
@EditorHidden
@Serializable
@SerialName("hollowengine:anchor/world")
data class WorldAnchor(
    val chunkX: Int = 0,
    val chunkZ: Int = 0,
    val localId: @Serializable(ForUuid::class) UUID = EMPTY_UUID,
) : AnchorComponent

@Registerable
@EditorHidden
@Serializable
@SerialName("hollowengine:anchor/primary")
data class PrimaryAnchorObject(
    val value: Boolean = true,
)
