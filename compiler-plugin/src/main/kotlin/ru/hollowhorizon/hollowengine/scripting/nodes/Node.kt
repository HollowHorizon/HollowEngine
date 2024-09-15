package ru.hollowhorizon.hollowengine.scripting.nodes

import java.io.File

fun interface Node {
    // Возвращает, выполнен ли код в ноде
    fun execute(): Boolean

    fun serialize() {}

    fun deserialize() {}

    open fun reset() {}
}

// По какой-то странной причине компилятор не может сам привести первый тип ко второму, так что есть разве что такой варинат по приведению типов
// Как через Kotlin IR генерировать сразу функциональные интерфейсы я без понятия
fun (() -> Boolean).toNode(): Node = Node { this() }