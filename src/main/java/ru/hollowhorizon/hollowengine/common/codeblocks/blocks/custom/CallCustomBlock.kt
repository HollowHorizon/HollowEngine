package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.blockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.CodeBlockInterpreter

@Serializable
@SerialName("hollowengine:custom/call_custom_block")
class CallCustomBlock(val function: String) : StatementBlock() {

    override suspend fun execute() {
        val context = blockContext()
        val function = context.functions[function] ?: error("Function not found!")

        val interpreter = CodeBlockInterpreter<Unit>(function)

        // Интерпретатор уже работает в скоупе текущего блока, сама функция создаёт свой контекст при вызове,
        // так что эту команду в новый скоуп кидать не нужно
        interpreter.execute()
    }

    override fun InputSlotScope.composeContent() {
        Text(function) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}