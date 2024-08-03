package ru.hollowhorizon.hollowengine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.common.config.HollowConfig
import ru.hollowhorizon.hc.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry

@HollowMod
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()
    val config by hollowConfig(::EngineConfig, "hollowengine")

    init {
        LOGGER.info("Initializing Hollow Engine 2.0!")
        NodesRegistry
        PinsRegistry
    }
}

@Serializable
class EngineConfig : HollowConfig() {
    @SerialName("enable_mod_resources_in_ide")
    var enableModResources = true
}

object PacketConfig {
    const val packetSize = 104857600
    const val decodeSize = 8388608 * 100
    const val nbtSize = 2097152 * 100
    const val var21Size = 8
}