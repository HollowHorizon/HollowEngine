package ru.hollowhorizon.hollowengine.compiler.suspendable

class AsyncContext(
    val context: SuspendContext,
) {
    init {
        context.setProperty("\$isEnd", false)
        context.setProperty("\$lock", false)
    }

    external fun restart() // Пока не работает. Будет перезапускать задачу с начала
    external fun stop() // Пока не работает. Будет завершать async изнутри
}

class AsyncController(
    val script: AsyncContext.() -> Any?,
) {
    var isEnd = false
    var lock = false
    var result: Any? = null

    fun tick(context: AsyncContext) {
        isEnd = context.context.properties["\$isEnd"] as Boolean
        lock = context.context.properties["\$lock"] as Boolean

        if (isEnd || lock) return

        var result: Any?
        do {
            result = script(context)
        } while (result == ResumeState)

        if (result == SuspendState) {
            context.context.setProperty("\$lock", true)
            return
        }

        this.result = result
        context.context.setProperty("\$isEnd", true)
    }

    external fun start() // Запускает async
    external fun stop() // Останавливает async
    external fun pause() // Останавливает async без полного сброса
    external fun resume() // Продолжает async без полного сброса
    external fun join() // Ожидает завершения async
}

fun async(script: AsyncContext.() -> Any?) = AsyncController(script)