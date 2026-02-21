package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.getVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.setVariable
import ru.hollowhorizon.hollowengine.common.codeblocks.walk

@Serializable
@SerialName("hollowengine:events/set_global")
class SetGlobalVarBlock(override var variableName: String = "var") : StatementBlock(), VariableProvider {
    override val color: Color get() = CodeBlocksColors.GLOBALS

    override val expressionType get() = (inputs["value"] as? ExpressionBlock)?.expressionType ?: AnyType
    override val isGlobal = true

    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        val value = value()

        setVariable(variableName, true, value)
    }

    override fun InputSlotScope.composeContent() {
        Text("Присвоить (G):") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        // Поле ввода имени переменной
        TextField(variableName) {
            modifier.width(FitContent).margin(horizontal = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { variableName = it }
                .hint("Имя переменной").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get_global")
class GetGlobalVarBlock(var varName: String = "var") : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.GLOBALS

    override val expressionType: ExpressionType
        get() {
            val setVar = scope?.walk()?.filterIsInstance<SetVarBlock>()?.find { it.variableName == varName }
            return setVar?.expressionType ?: AnyType
        }

    override suspend fun execute(): Any? {
        return getVariable(varName, true)?.get()
    }

    override fun InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной (G)").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}

@Serializable
@SerialName("hollowengine:variables/get_inline_global")
class GetGlobalVarInlineBlock(val name: String) : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.GLOBALS

    override val expressionType: ExpressionType
        get() {
            val setVar = scope?.walk()?.filterIsInstance<SetVarBlock>()?.find { it.variableName == name }
            return setVar?.expressionType ?: AnyType
        }

    override suspend fun execute(): Any? {
        return getVariable(name, true)?.get()
    }

    override fun InputSlotScope.composeContent() {
        Text("Значение переменной (G)") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        Text("\"$name\"") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center)
                .margin(start = 5.dp.scaled()).regular()
        }
    }
}