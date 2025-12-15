package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:start")
class OnStartBlock : StatementBlock(), StartBlock {
    override suspend fun BlockContext.execute() {}

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("При запуске скрипта") {
            modifier.width(FitContent).align(AlignmentX.Center, AlignmentY.Center)
                .bold()
        }
    }
}