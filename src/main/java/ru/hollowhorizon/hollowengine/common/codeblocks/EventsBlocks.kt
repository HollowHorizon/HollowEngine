package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

class SendEventBlock(var eventName: String = "MyEvent") : CodeBlock(MdColor.PINK) {
    override suspend fun execute(context: BlockContext): Any? {
        val payload = inputs["payload"]?.execute(context)
        context.emitEvent(eventName, payload)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Send Event") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        TextField(eventName) {
            modifier.width(100.dp).margin(horizontal = 5.dp)
                .hint("Название сообщения")
                .onChange { eventName = it }
        }
        InputSlot("payload")
    }
}

class OnEventBlock(var eventName: String = "MyEvent") : CodeBlock(MdColor.PINK) {
    var restartOnTrigger = mutableStateOf(true)
    private var activeJob: Job? = null

    // Этот метод вызывается не через execute(), а движком при старте скрипта
    fun listen(context: BlockContext) {
        context.scope.launch {
            context.eventBus.collect { (name, _) ->
                if (name == eventName) {
                    if (restartOnTrigger.value) {
                        // Отменяем старую работу, если она есть
                        activeJob?.cancel()
                    }

                    // Если restart = false, мы просто запускаем параллельно (или игнорируем, если хотим)
                    // Здесь реализуем логику: "Запускаем новую задачу"
                    activeJob = launch {
                        // Выполнение цепочки, привязанной к этому блоку
                        next?.execute(context)
                    }
                }
            }
        }
    }

    // Это корневой блок, у него execute не должен вызываться предками, но если вызовется:
    override suspend fun execute(context: BlockContext) = Unit

    // Используем кастомный лейаут заголовка, чтобы добавить настройки
    override fun UiScope.composeHeaderLayout(scopeBuilder: (UiScope) -> BlockEditor.InputSlotScope, blockHeaderModifier: UiModifier.() -> Unit) {
        Column {
            modifier.apply(blockHeaderModifier) // применяем фон и отступы к колонке
            modifier.padding(10.dp)

            Row {
                modifier.width(Grow.Std).alignY(AlignmentY.Center)
                Text("При событии") { modifier.textColor(Color.WHITE) }

                Box { modifier.width(10.dp) }

                TextField(eventName) {
                    modifier.width(120.dp)
                        .onChange { eventName = it }
                        .hint("Название сообщения")
                        .colors(lineColor = Color.WHITE, textColor = Color.WHITE)
                }
            }

            Box { modifier.height(10.dp) }

            // Настройка поведения: Restart vs Parallel
            Row {
                modifier.onClick { restartOnTrigger.value = !restartOnTrigger.value }

                Box {
                    modifier
                        .size(16.dp, 16.dp)
                        .border(RectBorder(Color.WHITE, 2.dp))
                        .alignY(AlignmentY.Center)

                    if (restartOnTrigger.value) {
                        Box { modifier.margin(2.dp).size(Grow.Std, Grow.Std).background(RectBackground(Color.WHITE)) }
                    }
                }

                Text(if (restartOnTrigger.value) "Синхронно (Отменить прошлые)" else "Асинхронно") {
                    modifier.margin(start = 8.dp).textColor(Color.WHITE.withAlpha(0.8f))
                }
            }
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        // Пусто, так как мы переопределили composeHeaderLayout
    }
}