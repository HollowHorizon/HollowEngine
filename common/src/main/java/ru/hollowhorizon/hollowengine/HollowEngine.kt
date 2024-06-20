package ru.hollowhorizon.hollowengine

import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry

@HollowMod
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()

    init {
        LOGGER.info("Initializing Hollow Engine 2.0!")
        NodesRegistry
        PinsRegistry
    }
}