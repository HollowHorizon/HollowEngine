package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import ru.hollowhorizon.hollowengine.compiler.identifiers.*

@OptIn(UnsafeDuringIrConstructionAPI::class)
object StatementsTransformer {
    private val sequenceNode =
        FunctionTransformer.ctx.referenceClass(SequenceNode) ?: throw ClassNotFoundException(SequenceNode.asString())
    private val loopNode =
        FunctionTransformer.ctx.referenceClass(LoopNode) ?: throw ClassNotFoundException(LoopNode.asString())
    private val whenNode =
        FunctionTransformer.ctx.referenceClass(WhenNode) ?: throw ClassNotFoundException(WhenNode.asString())
    private val branchNode =
        FunctionTransformer.ctx.referenceClass(BranchNode) ?: throw ClassNotFoundException(BranchNode.asString())

    private val sequenceGetter = sequenceNode.owner.declarations.filterIsInstance<IrProperty>()
        .first { it.name.asString() == "nodes" }.getter
        ?: throw NoSuchFieldException("${SequenceNode.asString()}::nodes")
    private val sequenceProperties = sequenceNode.owner.declarations.filterIsInstance<IrProperty>()
        .first { it.name.asString() == "properties" }.getter
        ?: throw NoSuchFieldException("${SequenceNode.asString()}::properties")

    private val arrayListCall =
        FunctionTransformer.ctx.referenceClass(ArrayList) ?: throw ClassNotFoundException(ArrayList.asString())
    private val addFunction =
        arrayListCall.owner.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.name.asString() == "add" }.first { it.valueParameters.size == 1 }
    private val hashMapCall =
        FunctionTransformer.ctx.referenceClass(HashMap) ?: throw ClassNotFoundException(HashMap.asString())
    private val putFunction =
        hashMapCall.owner.declarations.filterIsInstance<IrSimpleFunction>().first { it.name.asString() == "put" }


    fun IrBlockBodyBuilder.transformStatements(body: List<IrStatement>) {
        // Создаём стейт машину
        val getter = irGet(irTemporary(irCall(sequenceNode.constructors.first()), "sequence"))

        for (stmt in body.flatMap { if (it is IrBlock) it.statements else listOf(it) }) {
            when (stmt) {
                is IrVariable -> transformVariable(stmt, getter) // Добавляем переменные в список
                is IrWhen -> transformWhen(stmt, getter) // Преобразуем if / when в ноду
                is IrCall -> { // Заменяем вызов функции на метод-ноду / лямбду
                    if (stmt.symbol.owner.annotations.hasAnnotation(Suspendable)) transformSuspendable(stmt, getter)
                    else transform(stmt, getter)
                }

                is IrLoop -> transformLoop(stmt, getter) // Преобразуем for / while / do-while в ноду
                else -> transform(stmt, getter) // Иначе превращаем всю инструкцию в лямбда-выражение
            }
        }

        +irReturn(getter) // Возвращаем стейт-машину
    }

    private fun IrBlockBodyBuilder.transformVariable(variable: IrVariable, getter: IrGetValue) {
        +variable
        +irCall(putFunction).apply {
            dispatchReceiver = irCall(sequenceProperties).apply {
                dispatchReceiver = getter
            }

            putValueArgument(0, irString(variable.name.asString()))
            putValueArgument(1, irGet(variable))
        }
    }

    private fun IrBlockBodyBuilder.transformWhen(stmt: IrWhen, getter: IrGetValue) {
        val branchNodes = irTemporary(
            irCall(arrayListCall.constructors.find { it.owner.valueParameters.isEmpty() }!!),
            nameHint = "branches"
        )
        val branchesGetter = irGet(branchNodes)

        stmt.branches.forEach { branch ->
            val conditionNode = simpleCall(context.irBuiltIns.booleanType) {
                +irReturn(branch.condition)
            }
            val branchBody =
                irLambda(sequenceNode.defaultType) {
                    when (val stmtBody = branch.result) {
                        is IrBlock -> transformStatements(stmtBody.statements)
                        else -> transformStatements(listOf(stmtBody))
                    }
                }
            +branchBody

            +irCall(addFunction).apply {
                dispatchReceiver = branchesGetter

                putValueArgument(0, irCall(branchNode.constructors.first()).apply {
                    putValueArgument(0, conditionNode)
                    putValueArgument(1, irCall((branchBody as IrFunctionExpression).function.symbol))
                })
            }
        }

        +irCall(addFunction).apply {
            dispatchReceiver = irCall(sequenceGetter).apply {
                dispatchReceiver = getter
            }

            putValueArgument(0, irCall(whenNode.constructors.first()).apply {
                putValueArgument(0, branchesGetter)
            })
        }
    }

    fun IrBlockBodyBuilder.transformSuspendable(stmt: IrCall, getter: IrGetValue) {
        +irCall(addFunction).apply {
            dispatchReceiver = irCall(sequenceGetter).apply {
                dispatchReceiver = getter
            }
            putValueArgument(0, irCall(stmt.symbol).apply {
                stmt.valueArguments.forEachIndexed { index, irExpression ->
                    putValueArgument(index, irExpression)
                }
            })
        }
    }

    fun IrBlockBodyBuilder.transformLoop(stmt: IrLoop, getter: IrGetValue) {
        val conditionNode = simpleCall(context.irBuiltIns.booleanType) {
            +irReturn(stmt.condition)
        }
        val loopBody =
            simpleCall(sequenceNode.defaultType) {
                when (val stmtBody = stmt.body) {
                    is IrBlock -> {
                        transformStatements(stmtBody.statements)
                    }

                    else -> +stmtBody!!
                }
            }

        +irCall(addFunction).apply {
            dispatchReceiver = irCall(sequenceGetter).apply {
                dispatchReceiver = getter
            }

            putValueArgument(0, irCall(loopNode.constructors.first()).apply {
                putValueArgument(0, conditionNode)
                putValueArgument(1, loopBody)
                putValueArgument(2, irBoolean(stmt is IrDoWhileLoop))
            })
        }
    }

    fun IrBlockBodyBuilder.transform(stmt: IrStatement, getter: IrGetValue) {
        +irCall(addFunction).apply {
            dispatchReceiver = irCall(sequenceGetter).apply {
                dispatchReceiver = getter
            }

            putValueArgument(0, simpleCall(context.irBuiltIns.booleanType) {
                +stmt
                +irReturn(irBoolean(true))
            })
        }
    }
}

fun IrBuilderWithScope.simpleCall(
    returnType: IrType,
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    body: IrBlockBodyBuilder.() -> Unit,
): IrExpression {
    val function = context.irFactory.buildFun {
        this.startOffset = SYNTHETIC_OFFSET
        this.endOffset = SYNTHETIC_OFFSET
        this.returnType = returnType
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        name = SpecialNames.ANONYMOUS
        visibility = DescriptorVisibilities.LOCAL
    }.apply {
        this.parent = this@simpleCall.scope.getLocalDeclarationParent()
        val bodyBuilder = DeclarationIrBuilder(context, symbol, startOffset, endOffset)
        this.body = bodyBuilder.irBlockBody {
            body()
        }
    }

    val lambda = IrFunctionExpressionImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = FunctionTransformer.ctx.function(function.valueParameters.size).typeWith(
            function.valueParameters.map { it.type } + listOf(function.returnType)
        ),
        origin = IrStatementOrigin.LAMBDA,
        function = function
    )

    val nodeConverter = FunctionTransformer.ctx.referenceFunctions(ToNode).first()

    return irCall(nodeConverter).apply {
        extensionReceiver = lambda
    }
}

fun IrPluginContext.function(arity: Int): IrClassSymbol =
    referenceClass(ClassId(FqName("kotlin"), Name.identifier("Function$arity")))!!