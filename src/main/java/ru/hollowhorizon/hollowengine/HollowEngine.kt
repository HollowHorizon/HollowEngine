package ru.hollowhorizon.hollowengine

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.InitializeParams
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.ai.ShapesIncApi
import ru.hollowhorizon.hollowengine.common.config.HollowConfig
import ru.hollowhorizon.hollowengine.common.config.hollowConfig
import ru.hollowhorizon.hollowengine.common.project.kt.KotlinLanguageClient
import ru.hollowhorizon.hollowengine.common.project.kt.KotlinLanguageServer
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.example.HollowScript
import ru.hollowhorizon.hollowengine.common.scripting.core.setupScripting
import ru.hollowhorizon.hollowengine.common.scripting.events.loadEvents

//? if forge
/*import ru.hollowhorizon.hollowengine.client.render.setupCamera*/

@Init
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()
    val config by hollowConfig(::EngineConfig, "hollowengine")
    val shapesApi = ShapesIncApi(config.shapesToken)

    init {
        setupScripting()

        runBlocking {
            ScriptingCompiler.compileText<HollowScript>("ru.hollowhorizon.hc.LOGGER.info(\"Scripting engine loaded!\")")
                .execute()
        }

        LOGGER.info("Initializing Hollow Engine 2.0!")

        loadEvents()

        //? if forge {
        /*if(isPhysicalClient) setupCamera()
        *///?}

        KotlinLanguageServer.initialize(InitializeParams().apply {
        })
        KotlinLanguageServer.connect(KotlinLanguageClient)
    }
}


@Serializable
class EngineConfig : HollowConfig() {
    @SerialName("ide_config")
    var ideConfig = IDEConfig()

    @SerialName("shapes_token")
    val shapesToken = ""

    @Serializable
    class IDEConfig {
        var tabSpace = 4
        var fontSize = 30
        var enableSound = false
        var indexClasses = true
    }
}