package ru.hollowhorizon.hc.common.config

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hc.HollowCore

@Serializable
class HollowCoreConfig : HollowConfig() {
    val debugMode = false

    val inventory = InventoryConfig()
    val scripting = Scripting()
}

@Serializable
class InventoryConfig {
    var enableItemCounts = true
    var enableItemRotation = true
}

@Serializable
class Scripting {
    var includeMods = mutableListOf("hollowcore", "hollowengine") + platformMods
}

private val platformMods = when (HollowCore.platform) {
    HollowCore.Platform.FABRIC -> arrayOf("fabric-api")
    HollowCore.Platform.FORGE -> arrayOf("forge", "minecraft")
    HollowCore.Platform.NEOFORGE -> arrayOf("neoforge", "minecraft")
}
