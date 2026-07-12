package ru.hollowhorizon.hollowengine.common.plugin.ir.transformers

import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.common.plugin.ir.StateIrGenerationExtension.Companion.pluginContext
import ru.hollowhorizon.hollowengine.common.plugin.ir.util.builder

private val transitionCallableId = CallableId(
    packageName = FqName("ru.hollowhorizon.hollowengine.common.scripting.state"),
    callableName = Name.identifier("transition"),
)

private val transitionSymbol: IrSimpleFunctionSymbol by lazy {
    pluginContext.finderForBuiltins()
        .findFunctions(transitionCallableId)
        .singleOrNull { symbol ->
            val function = symbol.owner

            function.isSuspend &&
                    function.parameters.any { parameter ->
                        parameter.kind == IrParameterKind.Regular &&
                                parameter.name.asString() == "target" &&
                                parameter.type == pluginContext.irBuiltIns.stringType
                    }
        }
        ?: error("Cannot find suspend function $transitionCallableId")
}

class StateCallTransformer : IrElementTransformerVoid() {
    private val stateFunctions = mutableListOf<IrFunction>()

    override fun visitFunction(declaration: IrFunction): IrStatement {
        val isStateFunction = declaration.hasStateAnnotation() && declaration.isSuspend

        if (isStateFunction) stateFunctions.push(declaration)

        val result = super.visitFunction(declaration)

        if (isStateFunction) stateFunctions.pop()

        return result
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        val target = expression.symbol.owner

        val stateName = target.getStateNameOrNull()
            ?: return super.visitCall(expression)

        if (!target.isSuspend) {
            return super.visitCall(expression)
        }

        val currentStateFunction = stateFunctions.lastOrNull()
            ?: return super.visitCall(expression)

        val builder = currentStateFunction.builder()

        val transitionCall = builder.irCall(transitionSymbol).apply {
            val targetParameter = symbol.owner.parameters.single {
                it.kind == IrParameterKind.Regular &&
                        it.name.asString() == "target"
            }

            arguments[targetParameter] = builder.irString(stateName)
        }

        return IrReturnImpl(
            expression.startOffset,
            expression.endOffset,
            pluginContext.irBuiltIns.anyNType,
            currentStateFunction.symbol,
            transitionCall,
        )
    }
}

private val stateAnnotationFqName = FqName("ru.hollowhorizon.hollowengine.common.scripting.annotations.State")

private fun IrFunction.hasStateAnnotation(): Boolean {
    return annotations.any { it.isAnnotation(stateAnnotationFqName) }
}

private fun IrFunction.getStateNameOrNull(): String? {
    val annotation = annotations.firstOrNull { it.isAnnotation(stateAnnotationFqName) }
        ?: return null

    val explicitName = annotation.getStringArgumentByName("name")

    return explicitName
        ?.takeIf { it.isNotBlank() }
        ?: name.asString()
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrConstructorCall.getStringArgumentByName(parameterName: String): String? {
    val argument = getValueArgument(Name.identifier(parameterName)) ?: return null

    return (argument as? IrConst)?.value as? String
}