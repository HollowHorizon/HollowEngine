package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom

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
@SerialName("hollowengine:custom/call_custom_block")
class CallCustomBlock(val function: String) : CodeBlock() {

    override suspend fun BlockContext.execute() {
        // TODO: Add custom block interpreter with saving?
        functions[function].apply {
            execute()
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text(function) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}