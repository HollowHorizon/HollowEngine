package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.util.KotlinLikeDumpOptions
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineClassGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.CoroutineFunctionTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.CoroutinePropertyTransformer
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator

lateinit var pluginContext: IrPluginContext

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, context: IrPluginContext) {
        pluginContext = context

        moduleFragment.transform(ScriptRelocator(), null)

        val generator = CoroutineClassGenerator()
        moduleFragment.transform(generator, null)
        moduleFragment.transform(CoroutineFunctionTransformer(generator.functionToClass), null)
        moduleFragment.transform(CoroutinePropertyTransformer(generator.functionToClass), null)

        moduleFragment.files.forEach {
            println("File ${it.name}:")
            println(it.dumpKotlinLike(KotlinLikeDumpOptions(normalizeNames = true)))
        }
    }

}