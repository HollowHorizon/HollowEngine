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
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

@Serializable
@SerialName("hollowengine:loops/while")
class WhileBlock : CodeBlock(), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        while (context.scope.isActive && inputs["cond"]?.execute(context) as? Boolean == true) {
            inputs["body"]?.execute(context)
            // Небольшая задержка, чтобы не повесить поток при бесконечном цикле без yield
            delay(1)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Пока") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("cond", ExpressionTypes.BOOLEAN)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}

@Serializable
@SerialName("hollowengine:control/delay")
class DelayBlock : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        val time = (inputs["time"]?.execute(context).toString().toLongOrNull() ?: 1L) * 1000L
        delay(time)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Ждать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("time", ExpressionTypes.NUMBER)
        Text("секунд") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
    }
}