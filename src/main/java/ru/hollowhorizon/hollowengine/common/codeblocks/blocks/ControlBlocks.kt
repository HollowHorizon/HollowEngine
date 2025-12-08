package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ContainerBlock

@Serializable
@SerialName("hollowengine:loops/while")
class WhileBlock : CodeBlock(), ContainerBlock {
    val condition by input<Boolean>("cond")
    val body by input<Unit>("body")

    override suspend fun BlockContext.execute() {
        while (scope.isActive && condition()) {
            body()
            // Небольшая задержка, чтобы не повесить поток при бесконечном цикле без yield
            delay(1)
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Пока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(condition)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}

@Serializable
@SerialName("hollowengine:control/delay")
class DelayBlock : CodeBlock() {
    val time by input<Number>("time")

    override suspend fun BlockContext.execute() {
        delay((time().toDouble() * 1000).toLong())
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Ждать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(time)
        Text("секунд") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}