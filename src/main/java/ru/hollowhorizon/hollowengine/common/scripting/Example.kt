package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.scripting.Suspendable
import ru.hollowhorizon.hollowengine.scripting.nodes.Node

@Suspendable
fun suspendableMethod() {
    val user = readln()
    println("Hello, $user!")
    println("How are you?")
    val responce = readln()

    if (responce == "Ok") {
        println("Good, $user!")
    } else {
        for (i in 0..10) println("Bad, $user!")
    }
}

fun main() {
    val node = Class.forName("ru.hollowhorizon.hollowengine.common.scripting.ExampleKt")
        .declaredMethods.first { it.name == "suspendableMethod" }
        .invoke(null) as Node

    println(node)
}