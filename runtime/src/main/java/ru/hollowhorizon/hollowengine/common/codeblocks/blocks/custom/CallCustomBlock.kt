package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom

import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlocksScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.DynamicDisplayNameProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile

@Serializable
@SerialName("hollowengine:custom/call_custom_block")
class CallCustomBlock(val function: String) : StatementBlock(), DynamicDisplayNameProvider {
    override val color: Color get() = CodeBlocksColors.FUNCTIONS

    override suspend fun execute() {
        val file = currentFile()
        val function = file.functions[function] ?: error("Function not found!")

        val interpreter = CodeBlockInterpreter<Unit>(function)

        // Интерпретатор уже работает в скоупе текущего блока, сама функция создаёт свой контекст при вызове,
        // так что эту команду в новый скоуп кидать не нужно
        interpreter.execute()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.block.call_function".lang(function))
    }

    override fun resolveDisplayName(scope: BlocksScope): String = "hollowengine.gui.codeblocks.block.call_function".lang(function)
}
