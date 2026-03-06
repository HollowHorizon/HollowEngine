package ru.hollowhorizon.hollowengine.common.codeblocks.validation

import ru.hollowhorizon.hollowengine.common.codeblocks.flatten
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel

data class ValidationIssue(
    val blockId: String,
    val message: String,
)

fun interface ValidationRule {
    fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue>
}

class CodeBlockValidator(
    private val rules: List<ValidationRule>,
) {
    fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue> {
        if (rootBlocks.isEmpty()) return emptyList()
        return rules.flatMap { it.validate(rootBlocks) }
    }
}

fun List<BlockModel>.allBlocks(): List<BlockModel> = flatMap { it.flatten() }
