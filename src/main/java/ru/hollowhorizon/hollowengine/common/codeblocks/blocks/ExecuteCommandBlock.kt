package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

@Serializable
@SerialName("hollowengine:server/command")
class ExecuteCommandBlock : CodeBlock() {

    override suspend fun execute(context: BlockContext): Any? {
        val server = context.server
        val source = server.createCommandSourceStack()
        val command = inputs["cmd"]?.execute(context)
        if(command!=null) server.commands.performPrefixedCommand(source, command.toString())
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Команда:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("cmd", ExpressionTypes.STRING)
    }
}