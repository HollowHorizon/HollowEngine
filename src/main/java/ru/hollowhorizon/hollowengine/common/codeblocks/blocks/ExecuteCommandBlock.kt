package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.blockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:server/command")
class ExecuteCommandBlock : StatementBlock() {
    val cmd by input<String>("cmd")

    override suspend fun execute() {
        val context = blockContext()
        val source = context.server.createCommandSourceStack()
        context.server.commands.performPrefixedCommand(source, cmd())
    }

    override fun InputSlotScope.composeContent() {
        Text("Команда:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(cmd)
    }
}