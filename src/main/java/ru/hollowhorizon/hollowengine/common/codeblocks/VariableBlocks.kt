package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

class SetVarBlock(var varName: String = "var") : CodeBlock(MdColor.DEEP_ORANGE) {
    override suspend fun execute(context: BlockContext): Any? {
        val value = inputs["value"]?.execute(context)
        context.variables[varName] = value
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Set") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        // Поле ввода имени переменной
        TextField(varName) {
            modifier.width(80.dp).margin(horizontal = 5.dp)
                .onChange { varName = it }
                .hint("Имя переменной")
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("to") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("value", AnyType)
    }
}

class GetVarBlock(var varName: String = "var") : CodeBlock(MdColor.DEEP_ORANGE), ExpressionBlock {
    override val expressionType = ExpressionTypes.STRING

    override suspend fun execute(context: BlockContext): Any? {
        return context.variables[varName]
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Get") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        TextField(varName) {
            modifier.width(80.dp).margin(start = 5.dp)
                .onChange { varName = it }
                .hint("Имя переменной")
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}