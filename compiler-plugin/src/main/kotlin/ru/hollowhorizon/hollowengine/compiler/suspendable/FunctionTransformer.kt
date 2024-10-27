package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.declarations.builder.FirValueParameterBuilder
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsStatementOrigins
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.identifiers.United
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx


object FunctionTransformer {
    lateinit var ctx: IrPluginContext

    fun DeclarationIrBuilder.transformFunction(function: IrFunction) {
        function.transformChildrenVoid(PropertyTransformer(function))
        function.body = context.irBuiltIns.createIrBuilder(
            function.symbol, function.startOffset, function.endOffset
        ).irBlockBody {
            val context = TransformContext(this, function.valueParameters.last())
            transform(context, function.body?.statements ?: emptyList())
            context.build()
        }

        if (function.returnType.isUnit()) {
            val united = ctx.referenceClass(United)!!.constructors.first()
            function.annotations += irCall(united)
        }
        function.returnType = ctx.irBuiltIns.anyNType
    }

    fun transform(context: TransformContext, statements: List<IrStatement>) {
        statements.forEach { stmt ->
            when (stmt) {
                is IrLoop -> processLoop(context, stmt)
                is IrCall -> {
                    if (stmt.symbol == ctx.referenceFunctions(
                            CallableId(
                                FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("await")
                            )
                        ).first()
                    ) {
                        processAwait(context, stmt)
                    } else if (stmt.symbol.owner.annotations.hasAnnotation(Suspendable)) {
                        processSuspendable(context, stmt)
                    } else {
                        processStatement(context, stmt)
                    }
                }

                is IrTypeOperatorCall -> transform(context, listOf(stmt.argument))
                is IrBlock -> transform(context, stmt.statements)
                is IrReturn -> {
                    val value = stmt.value
                    if (value is IrCall && value.symbol.owner.annotations.hasAnnotation(Suspendable)) {
                        processSuspendable(context, value, true)
                    }
                }

                else -> processStatement(context, stmt)
            }
        }
    }

    private fun processSuspendable(context: TransformContext, stmt: IrCall, isReturn: Boolean = false) {

        val suspendContextType = ctx.referenceClass(SuspendContext)!!
        val setter = suspendContextType.functionByName("setProperty")
        val getter = suspendContextType.functionByName("getProperty")
        val remover = ctx.referenceClass(SuspendContext)!!.functionByName("removeProperty")

        val conditionIndex = (context.index).coerceAtLeast(0) + 1
        context.append { // Заканчиваем прошлое состояние и переходим к новому
            +irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                dispatchReceiver = irGet(context.suspendContext)
                putValueArgument(0, irInt(conditionIndex))
            }
            +irCall(setter).apply {
                dispatchReceiver = irGet(context.suspendContext)
                putValueArgument(0, irString("<inner-context>"))
                putValueArgument(1, irCall(suspendContextType.constructors.first()))
                putTypeArgument(0, suspendContextType.defaultType)
            }
        }
        context.suspend(true) // Останавливаем текущее состояние
        context.nextBranch {
            if(stmt.symbol.owner.valueParameters.last().type != ctx.referenceClass(SuspendContext)?.defaultType) {
                stmt.symbol.owner.returnType = ctx.irBuiltIns.anyNType
                stmt.symbol.owner.addValueParameter("suspendContext", ctx.referenceClass(SuspendContext)!!.defaultType)
            }
            val temp = irTemporary(irCall(stmt.symbol).apply {
                dispatchReceiver = stmt.dispatchReceiver
                extensionReceiver = stmt.extensionReceiver

                var i = 0
                stmt.valueArguments.forEach { arg ->
                    putValueArgument(i++, arg)
                }
                putValueArgument(i, irCall(getter).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irString("<inner-context>"))
                })

                stmt.typeArguments.forEachIndexed { i, arg ->
                    putTypeArgument(i, arg)
                }
            })
            if (isReturn) +irReturn(irGet(temp))
            else +irIfThenElse(ctx.irBuiltIns.unitType, irCall(ctx.irBuiltIns.ororSymbol).apply {
                putValueArgument(
                    0,
                    irEquals(irGet(temp), irGetObject(ctx.referenceClass(ResumeState)!!), IrStatementOrigin.EQEQEQ)
                )
                putValueArgument(
                    1,
                    irEquals(irGet(temp), irGetObject(ctx.referenceClass(SuspendState)!!), IrStatementOrigin.EQEQEQ)
                )
            }, irReturn(irGet(temp)), irBlock {
                +irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irInt(conditionIndex + 1))
                }
                +irCall(remover).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irString("<inner-context>"))
                }
                +irReturn(irGetObject(ctx.referenceClass(ResumeState)!!))

            })
        }
        context.nextBranch()
    }

    private fun processAwait(context: TransformContext, stmt: IrCall) {
        val conditionIndex = (context.index).coerceAtLeast(0) + 1
        context.append { // Заканчиваем прошлое состояние и переходим к новому
            +irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                dispatchReceiver = irGet(context.suspendContext)
                putValueArgument(0, irInt(conditionIndex))
            }
        }
        context.suspend(true) // Останавливаем текущее состояние
        context.nextBranch { // Замена `await(<true>)` на `if(<true>) index++` с переходом в следующее состояние
            +irIfThenElse(
                ctx.irBuiltIns.unitType,
                stmt.valueArguments.first()!!,
                irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irInt(conditionIndex + 1))
                },
                irReturn(irGetObject(ctx.referenceClass(SuspendState)!!))
            )
            +irReturn(irGetObject(ctx.referenceClass(ResumeState)!!))
        }
        context.nextBranch() // Создаём новое состояние для действий после `await`
    }

    private fun processLoop(context: TransformContext, loop: IrLoop) {
        val conditionIndex = (context.index).coerceAtLeast(0) + 1
        var returnIndex = -1
        context.append { // Заканчиваем прошлый раздел и переходим к новому
            +irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                dispatchReceiver = irGet(context.suspendContext)
                putValueArgument(0, irInt(conditionIndex))
            }
        }
        context.suspend(true) // Останавливаем функцию
        context.nextBranch { //Заменяем цикл на выражение с переходом к одной из двух фаз
            +irIfThenElse(ctx.irBuiltIns.unitType,
                loop.condition,
                irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irInt(conditionIndex + 1)) // if true -> branch 1
                },
                irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                    dispatchReceiver = irGet(context.suspendContext)
                    putValueArgument(0, irInt(returnIndex)) // else -> branch 2
                })
        }
        context.suspend(true)

        context.nextBranch()
        loop.body?.let {
            when (it) {
                is IrContainerExpression -> transform(context, it.statements)
                is IrCall -> transform(context, listOf(it))
                else -> error("Unexpected expression type: $it")
            }
        }

        context.append { //Возвращаемся к исходной проверке
            +irCall(ctx.referenceClass(SuspendContext)!!.getPropertySetter("index")!!).apply {
                dispatchReceiver = irGet(context.suspendContext)
                putValueArgument(0, irInt(conditionIndex))
            }
        }
        context.suspend()

        context.nextBranch()
        returnIndex = context.index
    }

    private fun processStatement(context: TransformContext, statement: IrStatement) {
        context.append { +statement }
    }
}

class TransformContext(
    private val builder: IrBlockBodyBuilder,
    val suspendContext: IrValueParameter,
    var index: Int = -1,
) {
    val branches = hashMapOf<Int, MutableList<IrStatementsBuilder<*>.() -> Unit>>()
    fun append(action: IrStatementsBuilder<*>.() -> Unit) {
        if (index == -1) nextBranch() // Create initial branch
        val branch = branches[index] ?: error("branch $index not exists!")
        branch.add(action)
    }

    fun nextBranch(branch: IrStatementsBuilder<*>.() -> Unit = EMPTY) {
        index++

        builder.apply {
            branches.computeIfAbsent(index) { mutableListOf() }.let { if (branch != EMPTY) it.add(branch) }
        }


    }

    fun build() {
        builder.apply {
            val eqeqeqInt = ctx.irBuiltIns.eqeqeqSymbol

            val call = irCall(
                ctx.referenceClass(SuspendContext)!!.getPropertyGetter("index")!!
            ).apply {
                dispatchReceiver = irGet(suspendContext)
            }
            val temp = irTemporary(call)


            +IrWhenImpl(startOffset,
                endOffset,
                context.irBuiltIns.unitType,
                JsStatementOrigins.COROUTINE_SWITCH,
                branches.map {
                    irBranch(irCall(eqeqeqInt).apply {
                        putValueArgument(0, irGet(temp))
                        putValueArgument(1, irInt(it.key))
                    }, irBlock {
                        it.value.forEach {
                            this.it()
                        }
                    })
                } + irElseBranch(throwIllegalStateException("Invalid index: ${branches.size}")))
        }
    }

    fun suspend(resume: Boolean = false) {
        append {
            if (resume) +irReturn(irGetObject(ctx.referenceClass(ResumeState)!!))
            else +irReturn(irGetObject(ctx.referenceClass(SuspendState)!!))
        }
    }

    companion object {
        private val EMPTY: IrStatementsBuilder<*>.() -> Unit = {}
    }
}

fun IrBuilderWithScope.throwIllegalStateException(message: String): IrExpression {
    // Находим конструктор IllegalStateException(String)
    val exceptionClass = context.irBuiltIns.illegalArgumentExceptionSymbol

    // Создаем выражение для вызова конструктора
    val exceptionConstructorCall = irCall(exceptionClass).apply {
        putValueArgument(0, irString(message))
    }

    // Создаем выражение для выброса исключения
    return irThrow(exceptionConstructorCall)
}