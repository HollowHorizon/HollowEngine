package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:custom/custom_block")
class CustomBlock(var function: String = ""): StatementBlock(), ContainerBlock, StartBlock, EndBlock {
    val body by input<Unit>("body")

    override suspend fun BlockContext.execute() {
        body()
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(function) {
            modifier.width(FitContent).margin(start = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { function = it }
                .hint("Имя функции").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}