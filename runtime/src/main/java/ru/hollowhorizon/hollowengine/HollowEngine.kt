package ru.hollowhorizon.hollowengine

//? if forge {
/*import ru.hollowhorizon.hollowengine.client.render.setupCamera
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
*///?}

import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.runtime.HollowRuntimeManager
import ru.hollowhorizon.hollowengine.common.scripting.CompilerLoader
import ru.hollowhorizon.hollowengine.common.scripting.deobf.CommonEnvironment
import java.io.File


@Init
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()
    val compilerLoader = CompilerLoader(DirectoryManager.HOLLOW_ENGINE.resolve("HollowEngineCompiler.jar").toFile())

    init {
        LOGGER.info("Initializing Hollow Engine 2.0!")
        HollowRuntimeManager.initialize()

        if (compilerLoader.hasCompilerJar()) {
            val (mappings, classpath) = CommonEnvironment.setup()
            compilerLoader.initialize(File(System.getProperty("java.home")), classpath, mappings)
        }

        //? if forge {
        /*if (isPhysicalClient) setupCamera()
        *///?}
    }
}
