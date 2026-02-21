package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.forget
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.remember
import kotlin.coroutines.coroutineContext

@Serializable
@SerialName("hollowengine:loops/while")
class WhileBlock : StatementBlock(), ContainerBlock {
    override val color: Color get() = CodeBlocksColors.LOOPS

    val condition by input<Boolean>("cond")
    val body by input<Unit>("body")

    override suspend fun execute() {
        while (coroutineContext.isActive && remember("condition") { condition() }) {
            body()
            forget("condition")
            yield() // Следующая итерация только в следующем тике
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Пока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(condition)
    }

    override fun InputSlotScope.composeBody() {
        BodySlot("body")
    }
}

@Serializable
@SerialName("hollowengine:control/delay")
class DelayBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.GENERAL

    val time by input<Number>("time")

    override suspend fun execute() {
        val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found")
        var remaining = frame.tag.getInt("remaining_ticks").takeIf { it > 0 } ?: (time().toFloat() * 20f).toInt()
        while (remaining > 0) {
            yield()
            frame.tag.putInt("remaining_ticks", --remaining)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Ждать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(time)
        Text("секунд") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}