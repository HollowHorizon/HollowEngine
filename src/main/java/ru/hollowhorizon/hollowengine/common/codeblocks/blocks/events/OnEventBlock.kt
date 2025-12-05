package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    override suspend fun execute(context: BlockContext) {
        context.eventBus.collect { (name, _) ->
            if (name == eventName) {
                if (restartOnTrigger.value) {
                    // Отменяем старую работу, если она есть
                    activeJob?.cancel()
                }

                // Если restart = false, мы просто запускаем параллельно (или игнорируем, если хотим)
                // Здесь реализуем логику: "Запускаем новую задачу"
                activeJob = context.scope.launch {
                    // Выполнение цепочки, привязанной к этому блоку
                    next?.execute(context)
                }
            }
        }
    }

    context(editor: BlockEditor)
    override fun UiScope.composeHeaderLayout(isHovered: Boolean, isGhost: Boolean, blockHeaderModifier: UiModifier.() -> Unit) {
        Column {
            modifier.apply(blockHeaderModifier) // применяем фон и отступы к колонке
            modifier.padding(10.dp)

            Row {
                modifier.width(Grow.Companion.Std).alignY(AlignmentY.Center)
                Text("При сообщении") { modifier.textColor(Color.Companion.WHITE) }

                Box { modifier.width(10.dp) }

                TextField(eventName) {
                    modifier.width(Grow.Companion.Std).alignY(AlignmentY.Center)
                        .onChange { eventName = it; editor.notifyChanged() }
                        .hint("Название сообщения")
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
                }
            }
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        // Пусто, так как мы переопределили composeHeaderLayout
    }
}