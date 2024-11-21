package ru.hollowhorizon.hollowengine.common.scripting.events

/**
 * Указывает что событие должно быть запущено только на клиенте
 */
@Target(AnnotationTarget.FILE)
annotation class ClientSide

/**
 * Указывает, что событие зависимое - т.е. Само не запустится, только через другие скрипты
 */
@Target(AnnotationTarget.FILE)
annotation class Dependent