package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:events/send")
class SendEventBlock(var eventName: String = "MyEvent") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

    override suspend fun execute() {
        //context.emitEvent(eventName, null)

    }

    override fun InputSlotScope.composeContent() {
        Text("Отправить сообщение") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(eventName) {
            modifier.width(FitContent).margin(horizontal = Dimensions.PaddingSmall.scaled())
                .hint("Название сообщения").font(font)
                .alignY(AlignmentY.Center)
                .onChange { eventName = it; notifyChanged() }
        }
    }
}

