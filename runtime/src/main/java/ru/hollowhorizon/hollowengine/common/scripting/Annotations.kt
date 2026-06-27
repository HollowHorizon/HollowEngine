package ru.hollowhorizon.hollowengine.common.scripting

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(val file: String)

/**
 * Should be used in `*.node.kts` scripts, runs method while component is active.
 * @param ticks Defines method call interval
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Every(val ticks: Int = 1)