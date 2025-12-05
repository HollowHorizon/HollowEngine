package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

class WhileBlock : CodeBlock(MdColor.ORANGE), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        while (context.scope.isActive && inputs["cond"]?.execute(context) as? Boolean == true) {
            inputs["body"]?.execute(context)
            // Небольшая задержка, чтобы не повесить поток при бесконечном цикле без yield
            delay(1)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("While") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("cond", ExpressionTypes.BOOLEAN)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}

class DelayBlock : CodeBlock(MdColor.LIGHT_BLUE) {
    override suspend fun execute(context: BlockContext): Any? {
        val time = inputs["time"]?.execute(context).toString().toLongOrNull() ?: 1000L
        delay(time)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Delay") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("time", ExpressionTypes.NUMBER)
        Text("ms") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
    }
}