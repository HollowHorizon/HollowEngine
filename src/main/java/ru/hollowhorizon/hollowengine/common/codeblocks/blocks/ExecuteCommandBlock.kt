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

@Serializable
@SerialName("hollowengine:server/command")
class ExecuteCommandBlock : CodeBlock() {
    val cmd by input<String>("cmd")

    override suspend fun BlockContext.execute() {
        val source = server.createCommandSourceStack()
        server.commands.performPrefixedCommand(source, cmd())
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Команда:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(cmd)
    }
}