package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class SuspendableTransformer : IrElementTransformerVoid() {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val suspendGetter = pluginContext.referenceClass(SuspendContext)!!.getPropertyGetter("index")!!
    val getter = pluginContext.referenceClass(SuspendContext)!!.functionByName("getProperty")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val asyncControllers = pluginContext.referenceClass(SuspendContext)!!.getPropertyGetter("asyncControllers")!!
    val asyncController = pluginContext.referenceClass(AsyncController)!!
    val asyncTick = asyncController.functionByName("tick")
    val contains = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashSet")))!!
        .functionByName("contains")

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.isSuspendable()) return super.visitFunction(declaration)
        val builder = pluginContext.irBuiltIns.createIrBuilder(
            declaration.symbol,
            declaration.startOffset,
            declaration.endOffset,
        )

        val suspendContext = declaration.suspendableContext

        val transformer = PropertyTransformer(declaration, suspendContext)
        declaration.transformChildrenVoid(transformer)

        val newBody = builder.irBlockBody {
            val stateVar = irCall(suspendGetter).apply {
                dispatchReceiver = suspendContext
            }

            val whenStatement = irWhen(context.irBuiltIns.unitType, listOf())
            +whenStatement

            declaration.body?.transform(
                SuspendCallTransformer(
                    WhenContext(
                        builder,
                        whenStatement,
                        stateVar,
                        suspendContext,
                        pluginContext
                    ),
                    transformer.controllers
                ), null
            )
        }
        declaration.body = newBody.apply {
            val actions = ArrayList<IrStatement>()

            transformer.controllers.forEachIndexed { i, it ->
                builder.apply {
                    actions.add(it)
                    actions.add(irIfThen(context.irBuiltIns.unitType, irCall(contains).apply {
                        dispatchReceiver = irCall(asyncControllers).apply {
                            dispatchReceiver = suspendContext
                        }

                        putValueArgument(0, irInt(i))
                    }, irCall(asyncTick).apply {
                        dispatchReceiver = irGet(it)

                        putValueArgument(0, irCall(getter).apply {
                            dispatchReceiver = suspendContext

                            putValueArgument(0, irString("<async_$i>"))
                            putTypeArgument(0, asyncController.defaultType)
                        })
                    }))
                }
            }


            statements.addAll(0, actions)
        }

        return super.visitFunction(declaration)
    }

}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrCall.isSuspendable() = symbol.owner.annotations.hasAnnotation(Suspendable.asSingleFqName())