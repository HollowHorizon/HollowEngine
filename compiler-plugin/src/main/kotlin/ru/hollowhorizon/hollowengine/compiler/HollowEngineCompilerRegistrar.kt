package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.*

@OptIn(ExperimentalCompilerApi::class)
class HollowEngineCompilerRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true
    private var isUsed = false

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (isUsed) return // WTF, how?

        IrGenerationExtension.registerExtension(HollowEngineGenerationExtension())
        isUsed = true
    }
}