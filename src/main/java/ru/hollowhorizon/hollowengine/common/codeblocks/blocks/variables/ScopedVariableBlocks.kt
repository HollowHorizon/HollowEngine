package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.InvertedExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableScope
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.getVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable

private fun InputSlotScope.variableNameField(value: String, onValueChanged: (String) -> Unit) {
    TextField(value) {
        modifier.margin(horizontal = 5.dp.scaled())
            .alignY(AlignmentY.Center)
            .onChange(onValueChanged)
            .hint("name")
            .font(font)
            .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
    }
}

private fun ExpressionBlock.resolveExpectedType(): ExpressionType {
    val parentInputType = parentBlock
        ?.takeIf { parentInputName != null }
        ?.inputTypes
        ?.get(parentInputName)
    if (parentInputType != null && parentInputType !== AnyType) return parentInputType

    val parentOutputType = parentBlock
        ?.takeIf { parentOutputName != null }
        ?.outputTypes
        ?.get(parentOutputName)
    if (parentOutputType != null && parentOutputType !== AnyType) return parentOutputType

    return AnyType
}

@Serializable
@SerialName("hollowengine:variables/set_global")
class SetGlobalVarBlock(var variableName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.LOCALS
    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        val fallbackType = (inputs["value"] as? ExpressionBlock)?.expressionType ?: AnyType
        setVariable(variableName, VariableScope.GLOBAL, value(), fallbackType)
    }

    override fun InputSlotScope.composeContent() {
        Text("set global") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        variableNameField(variableName) { variableName = it }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get_global")
class GetGlobalVarBlock(var variableName: String = "var") : ExpressionBlock(), InvertedExpressionBlock {
    override val color: Color get() = CodeBlocksColors.LOCALS
    override val expressionType: ExpressionType get() = resolveExpectedType()

    override suspend fun execute(): Any? {
        return getVariable(variableName, VariableScope.GLOBAL)?.get(expressionType)
    }

    override fun InputSlotScope.composeContent() {
        Text("global") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        variableNameField(variableName) { variableName = it }
    }
}

@Serializable
@SerialName("hollowengine:variables/set_entity")
class SetEntityVarBlock(var variableName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.LOCALS
    val entity by input<Entity>("entity")
    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        val fallbackType = (inputs["value"] as? ExpressionBlock)?.expressionType ?: AnyType
        setVariable(variableName, entity(), value(), fallbackType)
    }

    override fun InputSlotScope.composeContent() {
        Text("set entity") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        variableNameField(variableName) { variableName = it }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get_entity")
class GetEntityVarBlock(var variableName: String = "var") : ExpressionBlock(), InvertedExpressionBlock {
    override val color: Color get() = CodeBlocksColors.LOCALS
    override val expressionType: ExpressionType get() = resolveExpectedType()
    val entity by input<Entity>("entity")

    override suspend fun execute(): Any? {
        return getVariable(variableName, entity())?.get(expressionType)
    }

    override fun InputSlotScope.composeContent() {
        Text("entity") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        variableNameField(variableName) { variableName = it }
    }
}
