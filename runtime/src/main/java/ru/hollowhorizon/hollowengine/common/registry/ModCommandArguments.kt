package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.core.registries.BuiltInRegistries
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.bridge.commands.HollowArgumentTypes
import ru.hollowhorizon.hollowengine.common.commands.arguments.ScriptPathArgument

/**
 * Command argument types the engine adds.
 *
 * An argument type needs two things to reach players: its [ArgumentTypeInfo] in the registry, and an
 * entry in the map vanilla looks the type up by class in. Without the second one the argument node is
 * quietly dropped from the command tree sent to clients, and the command shows up as invalid there.
 */
@Init
object ModCommandArguments : HollowRegistry(HollowEngine.MODID) {
    private val SCRIPT_PATH_INFO: SingletonArgumentInfo<ScriptPathArgument> =
        SingletonArgumentInfo.contextFree(ScriptPathArgument::scriptPath)

    val SCRIPT_PATH: ArgumentTypeInfo<*, *> by register<ArgumentTypeInfo<*, *>>(
        "script_path",
        autoModel = null,
        registry = BuiltInRegistries.COMMAND_ARGUMENT_TYPE,
    ) { SCRIPT_PATH_INFO }

    init {
        HollowArgumentTypes.register(ScriptPathArgument::class.java, SCRIPT_PATH_INFO)
    }
}
