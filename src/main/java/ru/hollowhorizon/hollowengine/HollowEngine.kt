package ru.hollowhorizon.hollowengine

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.InitializeParams
import ru.hollowhorizon.hollowengine.api.Init
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