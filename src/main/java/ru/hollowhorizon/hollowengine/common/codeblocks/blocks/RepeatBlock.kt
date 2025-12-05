package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

class RepeatBlock : CodeBlock(MdColor.Companion.ORANGE), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        val times = inputs["times"]?.execute(context).toString().toIntOrNull() ?: 1
        repeat(times) {
            inputs["body"]?.execute(context)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Repeat") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center) }
        InputSlot("times", ExpressionTypes.BOOLEAN)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}