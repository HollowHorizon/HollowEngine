package ru.hollowhorizon.hollowengine


import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.api.Init


@Init
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()

    init {
        LOGGER.info("Initializing Hollow Engine 2.0!")
    }
}
