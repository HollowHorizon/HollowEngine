package ru.hollowhorizon.hollowengine

import imgui.ImGui
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.common.config.HollowConfig
import ru.hollowhorizon.hc.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.remember
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.example.HollowScript
import ru.hollowhorizon.hollowengine.common.scripting.core.setupScripting
import ru.hollowhorizon.hollowengine.common.scripting.events.loadEvents
import java.io.File
import kotlin.script.experimental.api.valueOrThrow

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
    @SerialName("show_mod_resources")
    var modsResources = true
}