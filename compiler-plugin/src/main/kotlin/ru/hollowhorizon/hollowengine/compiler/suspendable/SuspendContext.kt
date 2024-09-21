package ru.hollowhorizon.hollowengine.compiler.suspendable

import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.scripting.Suspendable

// TODO: Нужна какая-нибудь сериализация
/**
 * Суть такого подхода - сделать по-тиковую функцию, которая будет выполнять действие для текущего тика.
 * А внутри [SuspendContext] будут прописаны все данные, которые будут сохранены.
 */
class SuspendContext {
    val properties = mutableMapOf<String, Any?>()
    var index = 0
}

/**
 * Возвращается вместо результата функции, если она ещё не была выполнена.
 */
object SuspendedState

// TODO: Придумать что делать со всякими лямбдами: как вариант сделать "suspendable" лямбды. Кроме того нужно подумать что делать с функциями в духе `forEach`...
// TODO: Придумать что делать с циклами. Их контекст тоже будет сохранён

/**
 * Будет заменена на внутреннюю реализацию
 */
external fun await(condition: () -> Boolean)
