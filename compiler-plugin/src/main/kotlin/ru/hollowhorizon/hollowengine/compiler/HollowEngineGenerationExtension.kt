@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.CoroutineStateTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
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

        val stateTransformer = CoroutineStateTransformer(generator.functionToClass)
        val serializableTransformer = SerializablePropertiesTransformer()
        val lambdaTransformer = LambdaPropertiesTransformer(HashMap(generator.functionToClass))
        val restorableTransformer = RestorablePropertyTransformer()

        val localsTransformer = LocalPropertiesTransformer()

        val coroutines = generator.functionToClass.values
        val filter = coroutines.associateBy { it.coroutine }
        coroutines.filter { !it.coroutine.isInner }.forEach { info ->
            fun CoroutineTransformer.use(info: CoroutineGenerator, action: (CoroutineGenerator) -> Unit = {}) {
                coroutine = info
                info.invokeFunction.transform(this, null)
                info.invokeFunction.transformChildrenVoid(this)
                info.coroutine.declarations.filterIsInstance<IrClass>().forEach {
                    filter[it]?.let { use(it, action) }
                }
                action(info)
            }

            lambdaTransformer.use(info)
            stateTransformer.use(info)
            localsTransformer.use(info)
            serializableTransformer.filter = localsTransformer.locals
            serializableTransformer.use(info)
            restorableTransformer.filter = localsTransformer.locals
            restorableTransformer.use(info)
        }
        coroutines.forEach { info ->
            info.createSerializer()

            val fields = groupRestorableFields(info.branchMap)

            info.restoreFunction.body = info.restoreFunction.builder().irBlockBody {
                fields.forEach { (range, values) ->
                    val stateIndex = irGetField(irGet(info.receiver), info.stateIndex)
                    val condition = if (range.second < 0) irGreaterThan(stateIndex, irInt(range.first)) else {
                        irAnd(
                            irGreaterThan(stateIndex, irInt(range.first)),
                            irLessEqualThan(stateIndex, irInt(range.second))
                        )
                    }

                    +irIfThen(pluginContext.irBuiltIns.unitType, condition, irBlock {
                        values.forEach { (field, expression) ->

                            +irSetField(
                                irGet(info.receiver),
                                field,
                                expression.deepCopyWithSymbols(info.restoreFunction)
                            )
                        }
                    })
                }
            }

            println(info.coroutine.dumpKotlinLike())
        }
    }

}

fun irLessEqualThan(lhs: IrExpression, rhs: IrExpression): IrCall {
    val irBuiltIns = pluginContext.irBuiltIns
    return IrCallImpl.fromSymbolOwner(
        lhs.startOffset,
        lhs.endOffset,
        irBuiltIns.booleanType,
        irBuiltIns.lessOrEqualFunByOperandType.getValue(irBuiltIns.intClass)
    ).apply {
        putValueArgument(0, lhs)
        putValueArgument(1, rhs)
    }
}

fun irGreaterThan(lhs: IrExpression, rhs: IrExpression): IrCall {
    val irBuiltIns = pluginContext.irBuiltIns
    return IrCallImpl.fromSymbolOwner(
        lhs.startOffset,
        lhs.endOffset,
        irBuiltIns.booleanType,
        irBuiltIns.greaterFunByOperandType.getValue(irBuiltIns.intClass)
    ).apply {
        putValueArgument(0, lhs)
        putValueArgument(1, rhs)
    }
}

fun irAnd(first: IrExpression, second: IrExpression) = IrCallImpl.fromSymbolOwner(
    first.startOffset,
    first.endOffset,
    pluginContext.irBuiltIns.booleanType,
    pluginContext.irBuiltIns.andandSymbol
).apply {
    putValueArgument(0, first)
    putValueArgument(1, second)
}

fun irOr(first: IrExpression, second: IrExpression) = IrCallImpl.fromSymbolOwner(
    first.startOffset,
    first.endOffset,
    pluginContext.irBuiltIns.booleanType,
    pluginContext.irBuiltIns.ororSymbol
).apply {
    putValueArgument(0, first)
    putValueArgument(1, second)
}