package ru.hollowhorizon.hollowengine.common.scripting.compiler.story

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.*
import ru.hollowhorizon.hollowengine.common.scripting.compiler.story.FunctionTransformer.ctx


class FunctionPropertiesTransformer : IrElementTransformerVoid() {
    private val delegateType = ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.compiler.story"), Name.identifier("PropertyDelegate")
        )
    )!!
    private val delegateConstructor = delegateType.constructors.first()
    val properties = hashMapOf<IrValueSymbol, IrValueDeclaration>()
    private val ignoredExpressions = hashSetOf<IrGetValue>()
    val initializers = hashMapOf<IrVariable, IrExpression>()
    override fun visitValueParameter(parameter: IrValueParameter): IrStatement {
        val new = ctx.irFactory.createValueParameter(
            parameter.startOffset, parameter.endOffset, parameter.origin, parameter.name,
            ctx.function(0).typeWith(parameter.type), parameter.isAssignable, IrValueParameterSymbolImpl(),
            parameter.index, parameter.varargElementType, parameter.isCrossinline, parameter.isNoinline,
            parameter.isHidden
        ).apply {
            this.parent = parameter.parent
            properties[parameter.symbol] = this
        }

        return super.visitValueParameter(new)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        return super.visitSetValue(expression)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        properties[expression.symbol]?.let { delegate ->
            val func = delegate.parent as? IrFunction ?: return super.visitGetValue(expression)

            val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

            return builder.irCall(ctx.function(0).functionByName("invoke")).apply {
                dispatchReceiver = builder.irGet(delegate)
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol.owner.hasAnnotation(FqName("ru.hollowhorizon.hollowengine.common.scripting.story.StoryFunction"))) {
            val builder =
                ctx.irBuiltIns.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset)

            val newArgs = expression.valueArguments.filterNotNull().map { arg ->
                builder.irLambda(arg.type) {
                    +irReturn(arg)
                }
            }
            newArgs.forEachIndexed { index, irExpression ->
                expression.putValueArgument(index, irExpression)
            }
        }
        return super.visitCall(expression)
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        expression.transformChildren(this, null)
        return super.visitBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        expression.transformChildren(this, null)
        return super.visitWhen(expression)
    }
}

fun IrBuilderWithScope.irLambda(
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
        this.parent = this@irLambda.scope.getLocalDeclarationParent()
        val bodyBuilder = DeclarationIrBuilder(context, symbol, startOffset, endOffset)
        this.body = bodyBuilder.irBlockBody {
            body()
        }
    }

    val lambda = IrFunctionExpressionImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = ctx.function(function.valueParameters.size).typeWith(
            function.valueParameters.map { it.type } + listOf(function.returnType)
        ),
        origin = IrStatementOrigin.LAMBDA,
        function = function
    )


    return lambda
}