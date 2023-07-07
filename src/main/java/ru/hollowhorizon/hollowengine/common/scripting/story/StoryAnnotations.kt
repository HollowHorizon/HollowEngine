package ru.hollowhorizon.hollowengine.common.scripting.story

/**
 * Запускает текущий скрипт после указанного в параметре
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class AfterScript(val scriptPath: String)

/**
 * Запускает текущий скрипт сразу после входа в мир
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class FirstJoinScript

/**
 * Скрипт с этой аннотацией после завершения будет перезапущен заново
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class RepeatableScript