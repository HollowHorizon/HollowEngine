package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain

import java.util.*

enum class RecoveryAction {
    DROPPED_BLOCK,
    REMOVED_REFERENCE,
    REPLACED_WITH_STUB,
}

data class ScriptLoadIssue(
    val kind: Kind,
    val message: String,
    val action: RecoveryAction,
    val ownerBlockId: UUID? = null,
    val targetBlockId: UUID? = null,
) {
    enum class Kind {
        MISSING_NODE_FIELD,
        DECODE_FAILED,
        INVALID_REFERENCE_FORMAT,
        MISSING_NEXT_BLOCK,
        MISSING_INPUT_BLOCK,
        INVALID_NEXT_BLOCK_TYPE,
    }
}

