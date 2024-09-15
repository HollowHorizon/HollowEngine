package ru.hollowhorizon.hollowengine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.common.config.HollowConfig
import ru.hollowhorizon.hc.common.config.hollowConfig
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.HollowScript
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import java.io.File
import kotlin.script.experimental.api.valueOrThrow

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

suspend fun main() {
    val old = File("script.kts.jar")
    if (old.exists()) old.delete()
    val script = ScriptingCompiler.compileFile<HollowScript>(File("script.kts"))

    val result = script.execute {}

    val instance = result.valueOrThrow().returnValue.scriptInstance!!

    val test = instance::class.java.declaredMethods.find { it.name == "test" }



}

@Serializable
class EngineConfig : HollowConfig() {
    @SerialName("enable_mod_resources_in_ide")
    var enableModResources = true
}