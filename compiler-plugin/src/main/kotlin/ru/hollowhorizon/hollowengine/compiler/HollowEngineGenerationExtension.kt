@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.NameHelper
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.CoroutineStateTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KotlinClassMetadata

lateinit var pluginContext: IrPluginContext
lateinit var serializationContext: SerializationPluginContext

@OptIn(ObsoleteDescriptorBasedAPI::class)
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
        fun CoroutineTransformer.use(
            info: CoroutineGenerator,
            function: (CoroutineGenerator) -> IrFunction,
            action: (CoroutineGenerator) -> Unit = {},
        ) {
            coroutine = info
            function(info).transform(this, null)
            function(info).transformChildrenVoid(this)
            info.coroutine.declarations.filterIsInstance<IrClass>().forEach {
                filter[it]?.let { use(it, function, action) }
            }
            action(info)
        }

        coroutines.filter { !it.coroutine.isInner }.forEach { info ->


            lambdaTransformer.use(info, CoroutineGenerator::invokeFunction)
            stateTransformer.use(info, CoroutineGenerator::invokeFunction)
            localsTransformer.use(info, CoroutineGenerator::invokeFunction)
            serializableTransformer.filter = localsTransformer.locals
            serializableTransformer.use(info, CoroutineGenerator::invokeFunction)
            restorableTransformer.filter = localsTransformer.locals
            restorableTransformer.use(info, CoroutineGenerator::invokeFunction)
        }
        coroutines.forEach { info ->
            info.createSerializer()
            info.createAsyncs()

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
                            if (expression.type == pluginContext.irBuiltIns.unitType) {
                                +expression
                            } else {
                                +irSetField(
                                    irGet(info.receiver),
                                    field,
                                    expression.deepCopyWithSymbols(info.restoreFunction)
                                )
                            }
                        }
                    })
                }
            }

            serializableTransformer.use(info, CoroutineGenerator::restoreFunction)
            restorableTransformer.use(info, CoroutineGenerator::restoreFunction)

            println(info.coroutine.dumpKotlinLike())
        }

        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol in pluginContext.referenceFunctions(
                        CallableId(
                            FqName("ru.hollowhorizon.hollowengine.scripting"),
                            Name.identifier("script")
                        )
                    )
                ) {
                    val call = expression.getValueArgument(0) as? IrCall ?: return expression
                    val owner = call.symbol.owner
                    val className = ClassId.topLevel(FqName(NameHelper.createName(owner)))
                    val coroutine =
                        generator.functionToClass[owner]?.coroutine?.symbol ?: pluginContext.referenceClass(className)
                        ?: error("Class ${className.asSingleFqName().render()} not found!")
                    return owner.builder().irCall(coroutine.constructors.first())
                }
                return super.visitCall(expression)
            }
        })
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