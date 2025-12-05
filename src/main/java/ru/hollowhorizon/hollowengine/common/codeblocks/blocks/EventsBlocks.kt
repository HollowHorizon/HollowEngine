package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock

@Serializable
class SendEventBlock(var eventName: String = "MyEvent") : CodeBlock(MdColor.PINK) {
    override suspend fun execute(context: BlockContext): Any? {
        context.emitEvent(eventName, null)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Send Event") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        TextField(eventName) {
            modifier.width(100.dp).margin(horizontal = 5.dp)
                .hint("Название сообщения")
                .onChange { eventName = it; notifyChanged() }
        }
    }
}

@Serializable
class OnEventBlock(var eventName: String = "MyEvent") : CodeBlock(MdColor.PINK) {
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
    context(editor: BlockEditor)
    override fun UiScope.composeHeaderLayout(isHovered: Boolean, isGhost: Boolean, blockHeaderModifier: UiModifier.() -> Unit) {
        Column {
            modifier.apply(blockHeaderModifier) // применяем фон и отступы к колонке
            modifier.padding(10.dp)

            Row {
                modifier.width(Grow.Std).alignY(AlignmentY.Center)
                Text("При событии") { modifier.textColor(Color.WHITE) }

                Box { modifier.width(10.dp) }

                TextField(eventName) {
                    modifier.width(120.dp)
                        .onChange { eventName = it; editor.notifyChanged() }
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