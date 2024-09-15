package ru.hollowhorizon.hollowengine.compiler.story

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.DelegateProperty
import ru.hollowhorizon.hollowengine.compiler.story.FunctionTransformer.ctx
import kotlin.collections.set


class DelegatePropertiesTransformer : IrElementTransformerVoid() {
    private val delegateType =
        ctx.referenceClass(DelegateProperty) ?: throw ClassNotFoundException(DelegateProperty.asString())

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private val delegateConstructor = delegateType.constructors.first()
    private val properties = hashMapOf<IrValueSymbol, IrValueDeclaration>()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if ((declaration.type as? IrSimpleTypeImpl)?.classifier == delegateType) return super.visitVariable(declaration)
        val func = declaration.parent as? IrFunction ?: return super.visitVariable(declaration)
        val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

        val delegateInitializer = builder.irCall(delegateConstructor).apply {
            putClassTypeArgument(0, declaration.type)
            val initializer = declaration.initializer
            if (initializer != null) {
                putValueArgument(0, builder.irLambda(initializer.type) {
                    +irReturn(initializer)
                })
            }
        }
        val delegate = IrVariableImpl(
            builder.startOffset,
            builder.endOffset,
            IrDeclarationOrigin.DEFINED,
            IrVariableSymbolImpl(),
            declaration.name,
            delegateType.typeWith(declaration.type),
            true,
            isConst = false,
            isLateinit = false
        ).apply {
            parent = builder.scope.getLocalDeclarationParent()
            this.initializer = delegateInitializer
        }
        properties[declaration.symbol] = delegate

        return super.visitVariable(delegate)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        properties[expression.symbol]?.let { delegate ->
            val func = delegate.parent as? IrFunction ?: return super.visitSetValue(expression)

            val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

            expression.value.transform(this, null)

            return builder.irCall(delegateType.functionByName("set")).apply {
                dispatchReceiver = builder.irGet(delegate)
                putValueArgument(0, expression.value)
            }
        }

        return super.visitSetValue(expression)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        properties[expression.symbol]?.let { delegate ->
            val func = delegate.parent as? IrFunction ?: return super.visitGetValue(expression)

            val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

            return builder.irCall(delegateType.functionByName("get"), expression.type).apply {
                dispatchReceiver = builder.irGet(delegate)
            }

        }

        return super.visitGetValue(expression)
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

