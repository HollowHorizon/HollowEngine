package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.OutputConsumer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.InvertedExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableScope
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable

interface EventOutputVariableBinding {
    val variableName: String
    val variableScope: VariableScope
}

private fun InputSlotScope.eventOutputVariableNameField(
    value: String,
    onValueChanged: (String) -> Unit,
) {
    TextField(value) {
        modifier.width(FitContent)
            .margin(horizontal = 5.dp.scaled())
            .alignY(AlignmentY.Center)
            .onChange(onValueChanged)
            .hint("var")
            .font(font)
            .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
    }
}

private fun ExpressionBlock.resolveEventOutputType(): ExpressionType {
    val parentType = parentBlock
        ?.takeIf { parentOutputName != null }
        ?.outputTypes
        ?.get(parentOutputName)
    return parentType ?: AnyType
}

@Serializable
@SerialName("hollowengine:variables/event_output_local")
class EventOutputLocalVariableBlock(
    override var variableName: String = "var",
) : ExpressionBlock(), InvertedExpressionBlock, OutputConsumer, EventOutputVariableBinding {
    override val variableScope: VariableScope get() = VariableScope.LOCAL

    override val color: Color get() = CodeBlocksColors.LOCALS

    override val expressionType: ExpressionType
        get() = acceptedType

    override val acceptedType: ExpressionType
        get() = resolveEventOutputType()

    override suspend fun execute(): Any? = null

    override suspend fun accept(value: Any?) {
        if (variableName.isBlank()) return
        setVariable(variableName, variableScope, value, acceptedType)
    }

    override fun InputSlotScope.composeContent() {
        eventOutputVariableNameField(variableName) { this@EventOutputLocalVariableBlock.variableName = it }
        Text("local") {
            modifier.textColor(Color.WHITE)
                .alignY(AlignmentY.Center)
                .bold()
                .margin(start = 5.dp.scaled())
        }
    }
}

@Serializable
@SerialName("hollowengine:variables/event_output_global")
class EventOutputGlobalVariableBlock(
    override var variableName: String = "var",
) : ExpressionBlock(), InvertedExpressionBlock, OutputConsumer, EventOutputVariableBinding {
    override val variableScope: VariableScope get() = VariableScope.GLOBAL

    override val color: Color get() = CodeBlocksColors.LOCALS

    override val expressionType: ExpressionType
        get() = acceptedType

    override val acceptedType: ExpressionType
        get() = resolveEventOutputType()

    override suspend fun execute(): Any? = null

    override suspend fun accept(value: Any?) {
        if (variableName.isBlank()) return
        setVariable(variableName, variableScope, value, acceptedType)
    }

    override fun InputSlotScope.composeContent() {
        eventOutputVariableNameField(variableName) { this@EventOutputGlobalVariableBlock.variableName = it }
        Text("global") {
            modifier.textColor(Color.WHITE)
                .alignY(AlignmentY.Center)
                .bold()
                .margin(start = 5.dp.scaled())
        }
    }
}
