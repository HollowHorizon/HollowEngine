package ru.hollowhorizon.hollowengine

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hc.api.HollowMod
import ru.hollowhorizon.hc.common.config.HollowConfig
import ru.hollowhorizon.hc.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.registry.NodesRegistry
import ru.hollowhorizon.hollowengine.common.registry.PinsRegistry
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.example.HollowScript
import ru.hollowhorizon.hollowengine.common.scripting.core.setupScripting
import ru.hollowhorizon.hollowengine.scripting.Suspendable
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

        runBlocking { main() }
    }

    //write sort algorithm
    fun main() = runBlocking {

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
    @SerialName("show_mod_resources")
    var modsResources = true
}