package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

private val BROKEN_COLOR = Color(0.85f, 0.2f, 0.2f, 1f)

@Serializable
@SerialName("hollowengine:internal/broken_statement")
class BrokenStatementBlock(
    var reason: String = "Broken statement block",
    var originalType: String? = null,
) : StatementBlock() {
    override val color: Color get() = BROKEN_COLOR

    override suspend fun execute() = Unit

    override fun InputSlotScope.composeContent() {
        DefaultText("Error: $reason")
    }
}

@Serializable
@SerialName("hollowengine:internal/broken_expression")
class BrokenExpressionBlock(
    var reason: String = "Broken expression block",
    var originalType: String? = null,
) : ExpressionBlock() {
    override val color: Color get() = BROKEN_COLOR
    override val expressionType: ExpressionType get() = AnyType

    override suspend fun execute(): Any? = null

    override fun InputSlotScope.composeContent() {
        DefaultText("Error: $reason")
    }
}

