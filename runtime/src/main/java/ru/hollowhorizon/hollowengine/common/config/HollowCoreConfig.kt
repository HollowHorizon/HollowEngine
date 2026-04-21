package ru.hollowhorizon.hollowengine.common.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ru.hollowhorizon.hollowengine.common.utils.toml.toml

@ConfigName("hollowcore")
object HollowCoreConfig : Config() {
    @PropertyComment("Включить ли дебаг-режим?")
    val debugMode by property(false)
    val values by map(
        "hello" to true,
        "world" to false,
        "C://Users/Halva" to false
    )

    @PropertyComment("Настройки инвентаря")
    val inventory by property(InventoryConfig())

}

fun main() {
    val table = HollowCoreConfig.save()
    println(toml.encodeToString(table))
}

@Serializable
class InventoryConfig {
    var enableItemCounts = true
    var enableItemRotation = true
}