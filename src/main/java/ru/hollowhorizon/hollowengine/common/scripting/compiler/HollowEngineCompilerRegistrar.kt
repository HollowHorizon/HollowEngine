package ru.hollowhorizon.hollowengine.common.scripting.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.scripting.ScriptingCompilerPluginEvent

@OptIn(ExperimentalCompilerApi::class)
class HollowEngineCompilerRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(HollowEngineGenerationExtension())
    }
}

@OptIn(ExperimentalCompilerApi::class)
@SubscribeEvent
fun onLoadExtensions(event: ScriptingCompilerPluginEvent) {
    //event.addExtension(HollowEngineCompilerRegistrar())
}