package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionBlock

@Serializable
@SerialName("hollowengine:events/set")
class SetVarBlock(var varName: String = "var") : CodeBlock() {
    val value by input<Any>("value")

    override suspend fun BlockContext.execute() {
        val value = value()
        variables[varName] = value
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Присвоить:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        // Поле ввода имени переменной
        TextField(varName) {
            modifier.width(FitContent).margin(horizontal = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get")
class GetVarBlock(var varName: String = "var") : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = AnyType

    override suspend fun BlockContext.execute(): Any? {
        return variables[varName]
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}