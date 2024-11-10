package ru.hollowhorizon.hollowengine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.common.config.HollowConfig
import ru.hollowhorizon.hc.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import ru.hollowhorizon.hollowengine.common.scripting.core.setupScripting
import ru.hollowhorizon.hollowengine.common.scripting.events.loadEvents

@HollowMod
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()
    val config by hollowConfig(::EngineConfig, "hollowengine")

    init {
        setupScripting()

        LOGGER.info("Initializing Hollow Engine 2.0!")
        NodesRegistry
        PinsRegistry

        loadEvents()
    }
}


@Serializable
class EngineConfig : HollowConfig() {
    @SerialName("ide_config")
    var ideConfig = IDEConfig()

    @Serializable
    class IDEConfig {
        var tabSpace = 4
    }
}