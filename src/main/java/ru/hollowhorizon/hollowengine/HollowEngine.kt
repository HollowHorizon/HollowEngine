package ru.hollowhorizon.hollowengine

import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.ide.structure.ProjectStructure
import ru.hollowhorizon.hollowengine.common.ide.structure.ProjectStructureInitiator
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.example.HollowScript
import ru.hollowhorizon.hollowengine.common.scripting.core.setupScripting
import ru.hollowhorizon.hollowengine.common.scripting.events.loadEvents

//? if forge {
/*import ru.hollowhorizon.hollowengine.client.render.setupCamera
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
*///?}

@Init
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()
    val projectStructure: ProjectStructure

    init {
        setupScripting()
        projectStructure = ProjectStructureInitiator.initiateProjectStructure()

        runBlocking {
            ScriptingCompiler.compileText<HollowScript>("ru.hollowhorizon.hc.LOGGER.info(\"Scripting engine loaded!\")")
                .execute()
        }

        LOGGER.info("Initializing Hollow Engine 2.0!")

        loadEvents()

        //? if forge {
        /*if (isPhysicalClient) setupCamera()
        *///?}
    }
}