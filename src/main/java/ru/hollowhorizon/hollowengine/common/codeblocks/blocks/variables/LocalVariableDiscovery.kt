package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlocksScope
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.root
import ru.hollowhorizon.hollowengine.common.codeblocks.walk

interface LocalVariableDeclaration {
    var variableName: String
    val expressionType: ExpressionType
}

fun BlocksScope.findLocalDeclarations(): List<LocalVariableDeclaration> {
    return walk()
        .filterIsInstance<LocalVariableDeclaration>()
        .filter { it.variableName.isNotBlank() }
        .toList()
}

fun BlockModel.resolveLocalVariableType(
    variableName: String,
    excludeDeclaration: LocalVariableDeclaration? = null,
): ExpressionType {
    if (variableName.isBlank()) return AnyType

    val scope = scope ?: return AnyType
    val declarations = scope.findLocalDeclarations()
        .filter { it.variableName == variableName && it !== excludeDeclaration }

    if (declarations.isEmpty()) return AnyType

    val rootWalk = root.walk().toList()
    val currentIndex = rootWalk.indexOf(this).takeIf { it >= 0 } ?: Int.MAX_VALUE

    val fromCurrentRoot = declarations
        .filterIsInstance<BlockModel>()
        .mapNotNull { declaration ->
            val index = rootWalk.indexOf(declaration)
            if (index in 0..currentIndex) index to declaration else null
        }
        .maxByOrNull { it.first }
        ?.second as? LocalVariableDeclaration

    return fromCurrentRoot?.expressionType ?: declarations.last().expressionType
}
