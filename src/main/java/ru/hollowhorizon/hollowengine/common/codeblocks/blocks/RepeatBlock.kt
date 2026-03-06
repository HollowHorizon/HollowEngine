package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.remember
import kotlin.coroutines.coroutineContext

@Serializable
@SerialName("hollowengine:loops/repeat")
class RepeatBlock : StatementBlock(), ContainerBlock {
    override val color get() = CodeBlocksColors.LOOPS

    val times by input<Number>("times")
    val body by input<Unit>("body")

    override suspend fun execute() {
        val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found!")

        val repeatTimes = remember("times") { times().toInt() }
        val completedIterations = frame.tag.getInt("index")
        val remainingIterations = (repeatTimes - completedIterations).coerceAtLeast(0)

        repeat(remainingIterations) { iteration ->
            body()
            frame.tag.putInt("index", completedIterations + iteration + 1)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.repeat".lang) { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(times)
        Text("hollowengine.gui.codeblocks.label.times".lang) { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
    }

    override fun InputSlotScope.composeBody() {
        BodySlot("body")
    }
}
