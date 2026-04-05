package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.DynamicDisplayNameProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlocksScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.getVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockAnalysisService

@Serializable
@SerialName("hollowengine:events/set")
class SetVarBlock(var variableName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.LOCALS

    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        setVariable(variableName, value())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.variable_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(variableName) {
            modifier.width(FitContent).margin(horizontal = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { variableName = it }
                .hint("hollowengine.gui.codeblocks.label.variable_name".lang).font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get")
class GetVarBlock(var varName: String = "var") : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.LOCALS

    override val expressionType: ExpressionType
        get() = CodeBlockAnalysisService.resolveLocalVariableType(this, varName)

    override suspend fun execute(): Any? {
        return getVariable(varName, expressionType)
    }

    override fun InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("hollowengine.gui.codeblocks.label.variable_name".lang).font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}

@Serializable
@SerialName("hollowengine:variables/get_inline")
class GetVarInlineBlock(val name: String) : ExpressionBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.LOCALS

    override val expressionType: ExpressionType
        get() = CodeBlockAnalysisService.resolveLocalVariableType(this, name)

    override suspend fun execute(): Any? {
        return getVariable(name, expressionType)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.variable_name".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        Text("\"$name\"") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center)
                .margin(start = 5.dp.scaled()).regular()
        }
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "hollowengine.gui.codeblocks.block.get_var_named".lang.format(name)
}
