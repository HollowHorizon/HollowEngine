package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockAnalysisService

fun BlockModel.resolveLocalVariableType(variableName: String): ExpressionType {
    return CodeBlockAnalysisService.resolveLocalVariableType(this, variableName)
}
