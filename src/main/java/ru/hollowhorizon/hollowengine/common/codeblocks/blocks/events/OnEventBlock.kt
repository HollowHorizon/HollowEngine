package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.StartBlock

@Serializable
@SerialName("hollowengine:events/receive")
class OnEventBlock(var eventName: String = "MyEvent") : CodeBlock(), StartBlock {
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

    override suspend fun BlockContext.execute() {
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

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {

            Row {
                modifier.width(Grow.Companion.Std).alignY(AlignmentY.Center)
                Text("При сообщении") { modifier.textColor(Color.Companion.WHITE).bold() }

                Box { modifier.width(10.dp) }

                TextField(eventName) {
                    modifier.width(Grow.Companion.Std).alignY(AlignmentY.Center)
                        .onChange { eventName = it; notifyChanged() }
                        .hint("Название сообщения")
                        .font(font)
                        .colors(lineColor = Color.Companion.WHITE, textColor = Color.Companion.WHITE)
                }
            }

            Box { modifier.height(10.dp) }

            // Настройка поведения: Restart vs Parallel
            Row {
                modifier.onClick { restartOnTrigger.value = !restartOnTrigger.value }

                Checkbox(restartOnTrigger.use()) {
                    modifier.onToggle { restartOnTrigger.set(it) }
                        .alignY(AlignmentY.Center)
                }

                Text(if (restartOnTrigger.value) "Синхронно (Отменить прошлые)" else "Асинхронно") {
                    modifier.margin(start = 8.dp).textColor(Color.Companion.WHITE.withAlpha(0.8f))
                        .regular()
                }
            }
        }
    }
}