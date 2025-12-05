package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.StartBlock

@Serializable
@SerialName("hollowengine:start")
class OnStartBlock: CodeBlock(), StartBlock {
    override suspend fun execute(context: BlockContext): Any? {
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Начало") { modifier.width(Grow.Std).align(AlignmentX.Center, AlignmentY.Center) }
    }
}