package ru.hollowhorizon.hollowengine.common.plugin.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import ru.hollowhorizon.hollowengine.common.plugin.ir.transformers.StateCallTransformer

class StateIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        Companion.pluginContext = pluginContext
        moduleFragment.transform(StateCallTransformer(), null)
    }

    companion object {
        lateinit var pluginContext: IrPluginContext
            private set
    }
}