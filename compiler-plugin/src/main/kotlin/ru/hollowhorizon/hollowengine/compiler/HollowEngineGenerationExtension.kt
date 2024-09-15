package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import ru.hollowhorizon.hollowengine.compiler.story.FunctionTransformer
import ru.hollowhorizon.hollowengine.compiler.story.StoryEventTransformer

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        FunctionTransformer.ctx = pluginContext

        moduleFragment.transform(StoryEventTransformer(pluginContext), null)
    }
}