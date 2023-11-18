package ru.hollowhorizon.hollowengine.common.scripting.story

/**
 * Запускает текущий скрипт после указанного в параметре
 *
 * Runs current script after installed in [scriptPath]
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class AfterScript(val scriptPath: String)

/**
 * Запускает текущий скрипт сразу после входа в мир
 *
 * Runs current script after joining to world
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class FirstJoinScript

/**
 * Скрипт с этой аннотацией после завершения будет перезапущен заново
 *
 * Script with this annotation after end, can be started again
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class RepeatableScript