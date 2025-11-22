package ru.hollowhorizon.hollowengine.common.ide.session.diagnostic

import java.io.File

fun main() {
    File("C:\\Users\\Artem\\Modding\\HollowEngine\\src\\main\\java\\ru\\hollowhorizon\\hollowengine\\client\\kool")
        .walk()
        .filter { it.isFile }
        .forEach {
            println("File: ${it.name}")
            println("Content: \n${it.readText()}\n")
        }
}