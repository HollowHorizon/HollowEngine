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
    override suspend fun execute(context: BlockContext): Any? {
        val value = inputs["value"]?.execute(context)
        context.variables[varName] = value
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Присвоить") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        // Поле ввода имени переменной
        TextField(varName) {
            modifier.width(FitContent).margin(horizontal = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной")
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("value", AnyType)
    }
}

@Serializable
@SerialName("hollowengine:variables/get")
class GetVarBlock(var varName: String = "var") : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = AnyType

    override suspend fun execute(context: BlockContext): Any? {
        return context.variables[varName]
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной")
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}