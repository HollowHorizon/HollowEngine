package ru.hollowhorizon.hollowengine.compiler.suspendable

/**
 * Суть такого подхода - сделать по-тиковую функцию, которая будет выполнять действие для текущего тика.
 * А внутри [SuspendContext] будут прописаны все данные, которые будут сохранены.
 */
open class SuspendContext {
    var index = 0
    val properties = hashMapOf<String, Any?>()

    fun <T> setProperty(name: String, value: T) {
        properties[name] = value
    }

    fun <T> getProperty(name: String): T {
        return properties[name]!! as T
    }

    fun removeProperty(name: String) {
        properties.remove(name)
    }
}

class SuspendLauncher(val runnable: SuspendContext.() -> Any?) {
    val context = SuspendContext()
    var isEnd = false
    var result: Any? = null

    fun tick() {
        if (isEnd) return

        with(context) {
            var result = runnable()
            while (result == ResumeState) result = runnable()
            if(result == SuspendState) return
            isEnd = true
            this@SuspendLauncher.result = result
        }
    }
}

/**
 * Возвращается вместо результата функции, если она ещё не была выполнена.
 */
object ResumeState // В этом случае функция будет запущена повторно
object SuspendState // В этом случае функция будет запущена только в следующем тике
// Если же функция вернёт другое значение - значит это результат её выполнения


/**
 * Останавливает функцию до тех пор, пока не выполнится условие
 */
external fun await(condition: Boolean)
