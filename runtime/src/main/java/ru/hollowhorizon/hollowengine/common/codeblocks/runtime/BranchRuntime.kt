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
) {
    fun asRuntimeBranchKey(): String {
        val ownerToken = when (owner) {
            OwnerKey.Global -> "global"
            is OwnerKey.Entity -> "entity:${owner.uuid}"
        }
        return listOf(ownerToken, scriptPath, startBlockId.toString(), groupKey.orEmpty()).joinToString("|")
    }
}

internal fun UUID.toOwnerKey(): OwnerKey = OwnerKey.Entity(this)

internal fun OwnerKey.entityIdOrNull(): UUID? = (this as? OwnerKey.Entity)?.uuid

internal fun StartBlock.buildBranchKey(scriptPath: String, ownerKey: OwnerKey): BranchKey {
    val normalizedGroup = branchGroupKey?.trim().takeUnless { it.isNullOrEmpty() }
    return BranchKey(ownerKey, scriptPath, uuid, normalizedGroup)
}
