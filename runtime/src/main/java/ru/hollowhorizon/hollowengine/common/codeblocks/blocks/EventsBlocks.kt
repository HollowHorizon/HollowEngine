package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptSignal
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.SignalScope
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentFile
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentInstance

@Serializable
@SerialName("hollowengine:events/send")
class SendEventBlock(var eventName: String = "MyEvent") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS
    val payload by input<Any>("payload")
    var signalScope: SignalScope = SignalScope.LOCAL

    override suspend fun execute() {
        val instance = currentInstance()
        currentFile().system.emitSignal(
            ScriptSignal(
                name = eventName,
                scope = signalScope,
                owner = instance.ownerKey,
                sourceScriptPath = currentFile().path,
                payload = payload(),
            )
        )
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.send_message".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(eventName) {
            modifier.width(FitContent).margin(horizontal = Dimensions.PaddingSmall.scaled())
                .hint("hollowengine.gui.codeblocks.hint.signal_name".lang).font(font)
                .alignY(AlignmentY.Center)
                .onChange { eventName = it; notifyChanged() }
        }
        Box { modifier.width(Dimensions.PaddingSmall.scaled()) }
        Text(if (signalScope == SignalScope.LOCAL) "локально" else "глобально") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                .onClick {
                    signalScope = if (signalScope == SignalScope.LOCAL) SignalScope.GLOBAL else SignalScope.LOCAL
                    surface.triggerUpdate()
                    notifyChanged()
                }
        }
        Box { modifier.width(Dimensions.PaddingSmall.scaled()) }
        InputSlot(payload)
    }
}

@Serializable
@SerialName("hollowengine:events/call")
class CallEventBlock(var eventName: String = "MyEvent") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS
    val payload by input<Any>("payload")
    var signalScope: SignalScope = SignalScope.LOCAL

    override suspend fun execute() {
        val instance = currentInstance()
        currentFile().system.callSignal(
            ScriptSignal(
                name = eventName,
                scope = signalScope,
                owner = instance.ownerKey,
                sourceScriptPath = currentFile().path,
                payload = payload(),
            )
        )
    }

    override fun InputSlotScope.composeContent() {
        Text("Вызвать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        TextField(eventName) {
            modifier.width(FitContent).margin(horizontal = Dimensions.PaddingSmall.scaled())
                .hint("hollowengine.gui.codeblocks.hint.signal_name".lang).font(font)
                .alignY(AlignmentY.Center)
                .onChange { eventName = it; notifyChanged() }
        }
        Box { modifier.width(Dimensions.PaddingSmall.scaled()) }
        Text(if (signalScope == SignalScope.LOCAL) "локально" else "глобально") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                .onClick {
                    signalScope = if (signalScope == SignalScope.LOCAL) SignalScope.GLOBAL else SignalScope.LOCAL
                    surface.triggerUpdate()
                    notifyChanged()
                }
        }
        Box { modifier.width(Dimensions.PaddingSmall.scaled()) }
        InputSlot(payload)
    }
}
