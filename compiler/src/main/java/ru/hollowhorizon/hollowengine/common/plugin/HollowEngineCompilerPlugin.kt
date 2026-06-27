package ru.hollowhorizon.hollowengine.common.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import ru.hollowhorizon.hollowengine.common.plugin.ir.StateIrGenerationExtension

@OptIn(ExperimentalCompilerApi::class)
class HollowEngineCompilerPlugin : CompilerPluginRegistrar() {
    override val pluginId: String = "HollowEngineCompilerPlugin"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(StateIrGenerationExtension())
    }
}