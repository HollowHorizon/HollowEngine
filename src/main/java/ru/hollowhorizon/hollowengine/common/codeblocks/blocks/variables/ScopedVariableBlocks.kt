package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableScope
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.getVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockAnalysisService

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

@Serializable
@SerialName("hollowengine:variables/set_global")
class SetGlobalVarBlock(var variableName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.GLOBALS
    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        setVariable(variableName, VariableScope.GLOBAL, value())
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
class GetGlobalVarBlock(var variableName: String = "var") : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.GLOBALS
    override val expressionType: ExpressionType
        get() = CodeBlockAnalysisService.resolveGlobalVariableType(this, variableName)

    override suspend fun execute(): Any? {
        return getVariable(variableName, VariableScope.GLOBAL, expressionType)
    }

    override fun InputSlotScope.composeContent() {
        Text("global") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        variableNameField(variableName) { variableName = it }
    }
}

@Serializable
@SerialName("hollowengine:variables/set_entity")
class SetEntityVarBlock(var variableName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    val entity by input<Entity>("entity")
    val value by input<CompoundTag>("value")

    override suspend fun execute() {
        setVariable(variableName, entity(), value())
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
class GetEntityVarBlock(var variableName: String = "var") : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES
    override val expressionType: ExpressionType = typeOf<CompoundTag>()
    val entity by input<Entity>("entity")

    override suspend fun execute(): Any? {
        return getVariable(variableName, entity())
    }

    override fun InputSlotScope.composeContent() {
        Text("entity") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        variableNameField(variableName) { variableName = it }
    }
}
