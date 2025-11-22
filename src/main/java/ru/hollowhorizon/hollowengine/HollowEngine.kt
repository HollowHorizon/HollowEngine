package ru.hollowhorizon.hollowengine

import com.mojang.brigadier.Command.SINGLE_SUCCESS
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.commands.onRegisterCommands
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.scripting.CompilerLoader
import java.io.File

//? if forge {
/*import ru.hollowhorizon.hollowengine.client.render.setupCamera
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
*///?}

@Init
object HollowEngine {
    const val MODID = "hollowengine"
    val LOGGER = LogManager.getLogger()

    init {

        LOGGER.info("Initializing Hollow Engine 2.0!")



        //? if forge {
        /*if (isPhysicalClient) setupCamera()
        *///?}
    }
}

private val loader by lazy {
    CompilerLoader(File("C:\\Users\\Artem\\Modding\\HollowEngine\\merged\\HollowEngineCompiler-1.0.0.jar"))
}


@SubscribeEvent
fun commands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowegine" {
            "compiler" {
                "setup" {
                    executes {
                        loader.initialize(
                            File(System.getProperty("java.home")),
                            System.getProperty("java.class.path")
                                .split(File.pathSeparator)
                                .map(::File)
                        )

                        SINGLE_SUCCESS
                    }
                }

                "dispose" {
                    executes {
                        loader.close()

                        SINGLE_SUCCESS
                    }
                }
            }
        }
    }
}