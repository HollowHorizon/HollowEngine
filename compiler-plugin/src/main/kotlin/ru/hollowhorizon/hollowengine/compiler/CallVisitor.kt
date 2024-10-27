package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendLauncher
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable

class CallVisitor(private val context: IrPluginContext) : IrElementTransformerVoid() {
    private var functions = hashSetOf<IrFunction>()

    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
        if (expression.symbol == context.referenceClass(SuspendLauncher)?.constructors?.firstOrNull()) {
            functions += (expression.valueArguments.first() as IrFunctionExpression).function
        }
        return super.visitConstructorCall(expression)
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration in functions) {
            val builder = context.irBuiltIns.createIrBuilder(
                declaration.symbol,
                declaration.startOffset,
                declaration.endOffset
            )

            val declaration = SuspendableVisitor(context, declaration).visitFunction(declaration) as IrFunction
            declaration.returnType = context.irBuiltIns.anyNType
            val statement = declaration.body?.statements?.get(0) ?: error("Empty Suspend Launcher")
            declaration.body = builder.irBlockBody {
                when (statement) {
                    is IrReturn -> +statement
                    is IrCall -> +irReturn(statement) // Unit функция
                    else -> throw AssertionError("Unexpected statement $statement")
                }
            }

            return declaration
        }
        return super.visitFunction(declaration)
    }

    class SuspendableVisitor(private val context: IrPluginContext, private val function: IrFunction) :
        IrElementTransformerVoid() {

        @OptIn(UnsafeDuringIrConstructionAPI::class)
        override fun visitCall(expression: IrCall): IrExpression {
            val builder = context.irBuiltIns.createIrBuilder(
                expression.symbol,
                expression.startOffset,
                expression.endOffset
            )

            if (expression.symbol.owner.annotations.hasAnnotation(Suspendable)) {
                return builder.irCall(
                    expression.symbol
                ).apply {
                    var i = 0
                    expression.valueArguments.forEachIndexed { index, irExpression ->
                        putValueArgument(index, irExpression)
                        i++
                    }
                    putValueArgument(i, builder.irGet(function.extensionReceiverParameter!!))
                }
            }

            return super.visitCall(expression)
        }
    }
}