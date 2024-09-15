package ru.hollowhorizon.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import ru.hollowhorizon.compiler.story.FunctionTransformer
import ru.hollowhorizon.compiler.story.StoryEventTransformer

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        FunctionTransformer.ctx = pluginContext

        moduleFragment.transform(StoryEventTransformer(pluginContext), null)
    }
}