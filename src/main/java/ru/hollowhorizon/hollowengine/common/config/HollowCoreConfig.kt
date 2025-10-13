package ru.hollowhorizon.hollowengine.common.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ru.hollowhorizon.hollowengine.HollowCore
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

    @PropertyComment("Настройки скриптинга")
    val scripting by property(Scripting())
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

@Serializable
class Scripting {
    var includeMods = mutableListOf("hollowengine") + platformMods
}

private val platformMods = when (HollowCore.platform) {
    HollowCore.Platform.FABRIC -> arrayOf("fabric-api")
    HollowCore.Platform.FORGE -> arrayOf("forge", "minecraft")
    HollowCore.Platform.NEOFORGE -> arrayOf("neoforge", "minecraft")
}
