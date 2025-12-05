package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock

@Serializable
@SerialName("hollowengine:events/send")
class SendEventBlock(var eventName: String = "MyEvent") : CodeBlock() {
    override suspend fun execute(context: BlockContext): Any? {
        context.emitEvent(eventName, null)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Text("Отправить сообщение") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
            TextField(eventName) {
                modifier.width(FitContent).margin(horizontal = 5.dp)
                    .hint("Название сообщения")
                    .onChange { eventName = it; notifyChanged() }
            }
        }
    }
}

