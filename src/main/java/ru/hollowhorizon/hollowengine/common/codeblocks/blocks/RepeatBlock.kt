package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.remember
import kotlin.coroutines.coroutineContext

@Serializable
@SerialName("hollowengine:loops/repeat")
class RepeatBlock : StatementBlock(), ContainerBlock {
    val times by input<Int>("times")
    val body by input<Unit>("body")

    override suspend fun execute() {
        val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found!")

        val repeatTimes = remember("times") { times() }

        val expectedTimes = repeatTimes - frame.tag.getInt("index")

        repeat(expectedTimes) {
            body()
            frame.tag.putInt("index", it)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Повторить") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(times)
        Text("Раз") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
    }

    override fun InputSlotScope.composeBody() {
        BodySlot("body")
    }
}