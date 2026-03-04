package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.OutputConsumer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.InvertedExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable

@Serializable
@SerialName("hollowengine:variables/event_output_local")
class EventOutputVariableBlock(
    override var variableName: String = "var",
) : ExpressionBlock(), InvertedExpressionBlock, OutputConsumer, LocalVariableDeclaration {
    override val color: Color get() = CodeBlocksColors.LOCALS

    override val expressionType: ExpressionType
        get() = acceptedType

    override val acceptedType: ExpressionType
        get() = resolveLocalVariableType(variableName, excludeDeclaration = this)

    override suspend fun execute(): Any? = null

    override suspend fun accept(value: Any?) {
        if (variableName.isBlank()) return
        setVariable(variableName, value)
    }

    override fun InputSlotScope.composeContent() {
        TextField(variableName) {
            modifier.width(FitContent).margin(horizontal = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { variableName = it }
                .hint("var")
                .font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}
