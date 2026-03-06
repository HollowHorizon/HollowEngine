package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.UUID

@Serializable
sealed interface OwnerKey {
    @Serializable
    @SerialName("global")
    data object Global : OwnerKey

    @Serializable
    @SerialName("entity")
    data class Entity(@Serializable(ForStringUUID::class) val uuid: UUID) : OwnerKey
}

@Serializable
data class BranchKey(
    val owner: OwnerKey,
    val scriptPath: String,
    val startBlockId: @Serializable(ForStringUUID::class) UUID,
    val groupKey: String? = null,
)

@Serializable
enum class RepeatPolicy {
    PARALLEL,
    IGNORE,
    RESTART,
    QUEUE,
}

data class ActiveBranchSnapshot(
    val key: BranchKey,
    val repeatPolicy: RepeatPolicy,
    val state: BranchState,
    val currentBlockId: UUID?,
    val queueLength: Int,
)

enum class BranchState {
    RUNNING,
    FROZEN,
}

internal fun UUID.toOwnerKey(): OwnerKey = OwnerKey.Entity(this)

internal fun OwnerKey.entityIdOrNull(): UUID? = (this as? OwnerKey.Entity)?.uuid

internal fun StartBlock.buildBranchKey(scriptPath: String, ownerKey: OwnerKey): BranchKey {
    val normalizedGroup = branchGroupKey?.trim().takeUnless { it.isNullOrEmpty() }
    return BranchKey(ownerKey, scriptPath, uuid, normalizedGroup)
}
