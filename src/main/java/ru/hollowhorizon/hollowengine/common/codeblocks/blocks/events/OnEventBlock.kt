package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptSignalHandler
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.SignalScope
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentScriptSignal
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.skipScriptEventExecution
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.EventContextProvider

@Serializable
@SerialName("hollowengine:events/receive")
class OnEventBlock(var eventName: String = "MyEvent") : StartBlock(), ScriptSignalHandler, EventContextProvider {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val payloadOutput by outputDefault<Any>(
        name = PAYLOAD_OUTPUT,
        default = { EventOutputVariableBlock("payload") },
    )

    @SerialName("signal_scope")
    override var signalScope: SignalScope = SignalScope.LOCAL

    override val signalName: String
        get() = eventName

    override suspend fun trigger() {
        val signal = currentScriptSignal() ?: skipScriptEventExecution()
        if (signal.name != eventName || signal.scope != signalScope) skipScriptEventExecution()
        payloadOutput.emit(signal.payload)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.codeblocks.events.on_event".lang) { modifier.alignY(AlignmentY.Center).textColor(Color.WHITE).bold() }
        Box { modifier.width(Dimensions.PaddingNormal.scaled()) }
        TextField(eventName) {
            modifier.alignY(AlignmentY.Center)
                .onChange { eventName = it; notifyChanged() }
                .hint("hollowengine.codeblocks.events.event_name".lang)
                .font(font)
                .colors(lineColor = Color.WHITE, textColor = Color.WHITE)
        }
        Box { modifier.width(Dimensions.PaddingNormal.scaled()) }
        Text(if (signalScope == SignalScope.LOCAL) "local" else "global") {
            modifier.alignY(AlignmentY.Center).textColor(Color.WHITE).bold()
                .onClick {
                    signalScope = if (signalScope == SignalScope.LOCAL) SignalScope.GLOBAL else SignalScope.LOCAL
                    notifyChanged()
                }
        }
    }

    override fun availableEventOutputs(): Set<String> = setOf("payload")

    companion object {
        const val PAYLOAD_OUTPUT = "payloadOutput"
    }
}
