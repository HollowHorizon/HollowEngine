package ru.hollowhorizon.hollowengine.common.scripting.compiler.story

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
import org.jetbrains.kotlin.name.*

@OptIn(UnsafeDuringIrConstructionAPI::class)
object StatementsTransformer {
    val sequenceNode = FunctionTransformer.ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
            Name.identifier("SequenceNode")
        )
    )!!
    val loopNode = FunctionTransformer.ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
            Name.identifier("LoopNode")
        )
    )!!
    val whenNode = FunctionTransformer.ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
            Name.identifier("WhenNode")
        )
    )!!
    val branchNode = FunctionTransformer.ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
            Name.identifier("BranchNode")
        )
    )!!
    val sequenceGetter = sequenceNode.owner.declarations.filterIsInstance<IrProperty>()
        .first { it.name.asString() == "nodes" }.getter!!
    val sequenceProperties = sequenceNode.owner.declarations.filterIsInstance<IrProperty>()
        .first { it.name.asString() == "properties" }.getter!!

    val arrayListCall =
        FunctionTransformer.ctx.referenceClass(ClassId(FqName("java.util"), Name.identifier("ArrayList")))!!
    val addFunction =
        arrayListCall.owner.declarations.filterIsInstance<IrSimpleFunction>().filter { it.name.asString() == "add" }
            .first { it.valueParameters.size == 1 }
    val hashMapCall =
        FunctionTransformer.ctx.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashMap")))!!
    val putFunction =
        hashMapCall.owner.declarations.filterIsInstance<IrSimpleFunction>().first { it.name.asString() == "put" }


    val sequenceNodeCtor = sequenceNode.constructors.first()


    fun IrBlockBodyBuilder.transformStatements(body: List<IrStatement>) {
        val actionNode = irTemporary(
            irCall(sequenceNodeCtor),
            nameHint = "sequence"
        )
        val getter = irGet(actionNode)

        for (stmt in body.flatMap { if(it is IrBlock) it.statements else listOf(it) }) {
            if (stmt is IrVariable) {
                +stmt
                +irCall(putFunction).apply {
                    dispatchReceiver = irCall(sequenceProperties).apply {
                        dispatchReceiver = getter
                    }

                    putValueArgument(0, irString(stmt.name.identifier))
                    putValueArgument(1, irGet(stmt))
                }
                continue
            }

            if (stmt is IrWhen) {
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
                                is IrBlock -> {
                                    transformStatements(stmtBody.statements)
                                }

                                else -> {
                                    transformStatements(listOf(stmtBody))
                                }
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

                continue
            }

            if (stmt is IrCall && stmt.symbol.owner.annotations.hasAnnotation(FqName("ru.hollowhorizon.hollowengine.common.scripting.story.StoryFunction"))) {
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
            } else if (stmt is IrLoop) {
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
            } else {
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



        +irReturn(getter)

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

    val nodeConverter = FunctionTransformer.ctx.referenceFunctions(
        CallableId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
            Name.identifier("toNode")
        )
    ).first()

    return irCall(nodeConverter).apply {
        extensionReceiver = lambda
    }
}

fun IrPluginContext.function(arity: Int): IrClassSymbol =
    referenceClass(ClassId(FqName("kotlin"), Name.identifier("Function$arity")))!!