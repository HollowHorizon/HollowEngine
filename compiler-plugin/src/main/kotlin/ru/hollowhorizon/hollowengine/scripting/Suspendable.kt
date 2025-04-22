package ru.hollowhorizon.hollowengine.scripting

import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Suspendable

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class United

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class LambdaParameter

/**
 * Не меняет переменную на сериализуемый аналог. Используйте аккуратно, такие переменные невозможно вызвать после приостанавливаемых функций.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.LOCAL_VARIABLE)
annotation class Ignore
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.LOCAL_VARIABLE)
annotation class Restorable

object SuspendState
object ResumeState

external fun script(value: Any?) : SFunction0<Any?>