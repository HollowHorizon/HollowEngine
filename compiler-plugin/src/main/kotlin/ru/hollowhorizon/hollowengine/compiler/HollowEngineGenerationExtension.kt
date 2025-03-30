@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.KotlinLikeDumpOptions
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineClassGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.createSerializer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.LambdaPropertiesTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.SerializablePropertiesTransformer
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator

lateinit var pluginContext: IrPluginContext
lateinit var serializationContext: SerializationPluginContext

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, context: IrPluginContext) {
        pluginContext = context
        serializationContext = SerializationPluginContext(pluginContext, null)


        moduleFragment.transform(ScriptRelocator(), null)

        val generator = CoroutineClassGenerator()
        moduleFragment.transform(generator, null)

        //moduleFragment.transform(CoroutineStateTransformer(generator.functionToClass), null)

        val propertyTransformer = SerializablePropertiesTransformer()
        val lambdaTransformer = LambdaPropertiesTransformer(HashMap(generator.functionToClass))
        generator.functionToClass.values.forEach { info ->
            propertyTransformer.coroutine = info
            lambdaTransformer.coroutine = info

            info.updateFunction.transformChildrenVoid(propertyTransformer)
            info.updateFunction.transformChildrenVoid(lambdaTransformer)

            info.createSerializer()
            info.coroutine.declarations.sortBy {
                when(it) {
                    is IrField -> 0
                    is IrFunction -> 1
                    is IrClass -> 2
                    else -> 3
                }
            }
        }

        moduleFragment.files.forEach {
            println("File ${it.name}:")
            println(it.dumpKotlinLike(KotlinLikeDumpOptions(normalizeNames = true)))
        }
    }

}