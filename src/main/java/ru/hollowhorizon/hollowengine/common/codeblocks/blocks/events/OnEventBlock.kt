package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock

@Serializable
@SerialName("hollowengine:events/receive")
class OnEventBlock(var eventName: String = "MyEvent") : StartBlock() {
    @Transient
    var restartOnTrigger = mutableStateOf(true)

    @SerialName("restart_on_trigger")
    private var restart: Boolean
        get() = restartOnTrigger.value
        set(value) {
            restartOnTrigger.set(value)
        }

    @Transient
    private var activeJob: Job? = null

    override suspend fun trigger() {
        TODO("Not yet implemented")
    }

    override suspend fun execute() {
//        context.eventBus.collect { (name, _) ->
//            if (name == eventName) {
//                if (restartOnTrigger.value) {
//                    activeJob?.cancel()
//                }
//
//                activeJob = context.scope.launch {
//                    next?.execute(context)
//                }
//            }
//        }
    }

    override fun InputSlotScope.composeContent() {

        Text("При сообщении") { modifier.alignY(AlignmentY.Center).textColor(Color.WHITE).bold() }
        Box { modifier.width(sizes.gap) }
        TextField(eventName) {
            modifier.alignY(AlignmentY.Center)
                .onChange { eventName = it; notifyChanged() }
                .hint("Название сообщения")
                .font(font)
                .colors(lineColor = Color.WHITE, textColor = Color.WHITE)
        }
    }
}