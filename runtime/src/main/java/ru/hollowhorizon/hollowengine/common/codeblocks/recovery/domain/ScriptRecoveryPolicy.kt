package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain

enum class DecodeFailureStrategy {
    FAIL,
    DROP_BLOCK,
    REPLACE_WITH_STUB,
}

enum class MissingReferenceStrategy {
    FAIL,
    REMOVE_REFERENCE,
    REPLACE_WITH_STUB,
}

data class ScriptRecoveryPolicy(
    val decodeFailureStrategy: DecodeFailureStrategy,
    val missingReferenceStrategy: MissingReferenceStrategy,
) {
    companion object {
        fun strict() = ScriptRecoveryPolicy(
            decodeFailureStrategy = DecodeFailureStrategy.FAIL,
            missingReferenceStrategy = MissingReferenceStrategy.FAIL
        )

        fun lenient() = ScriptRecoveryPolicy(
            decodeFailureStrategy = DecodeFailureStrategy.DROP_BLOCK,
            missingReferenceStrategy = MissingReferenceStrategy.REPLACE_WITH_STUB
        )
    }
}

