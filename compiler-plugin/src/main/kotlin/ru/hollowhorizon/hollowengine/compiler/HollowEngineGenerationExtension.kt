package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendableParameterChanger
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendableTransformer

lateinit var pluginContext: IrPluginContext

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, context: IrPluginContext) {
        pluginContext = context

        moduleFragment.transform(ScriptRelocator(), null)
        moduleFragment.transform(SuspendableParameterChanger(), null)
        moduleFragment.transform(SuspendableTransformer(), null)
    }

}