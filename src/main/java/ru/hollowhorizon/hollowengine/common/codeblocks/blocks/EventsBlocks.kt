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
    override suspend fun BlockContext.execute() {
        //context.emitEvent(eventName, null)

    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Text("Отправить сообщение") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
            TextField(eventName) {
                modifier.width(FitContent).margin(horizontal = 5.dp)
                    .hint("Название сообщения").font(font)
                    .onChange { eventName = it; notifyChanged() }
            }
        }
    }
}

