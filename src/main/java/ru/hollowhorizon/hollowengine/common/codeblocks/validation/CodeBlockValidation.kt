package ru.hollowhorizon.hollowengine.common.codeblocks.validation

import ru.hollowhorizon.hollowengine.common.codeblocks.flatten
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel

data class ValidationIssue(
    val blockId: String,
    val message: String,
    val scriptPath: String? = null,
)

fun interface ValidationRule {
    fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue>
}

class CodeBlockValidator(
    private val rules: List<ValidationRule> = listOf(AnalysisValidationRule),
) {
    fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue> {
        if (rootBlocks.isEmpty()) return emptyList()
        return rules.flatMap { it.validate(rootBlocks) }
    }
}

object AnalysisValidationRule : ValidationRule {
    override fun validate(rootBlocks: List<BlockModel>): List<ValidationIssue> {
        return CodeBlockAnalysisService.analyze(rootBlocks).issues
    }
}

fun List<BlockModel>.allBlocks(): List<BlockModel> = flatMap { it.flatten() }
