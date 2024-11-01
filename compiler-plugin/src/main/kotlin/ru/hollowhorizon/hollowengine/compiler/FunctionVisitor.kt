package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer

/**
 * Трансформирует скрипт или функции таким образом, чтобы они могли быть сериализуемы.
 */
class FunctionVisitor(private val context: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.annotations.hasAnnotation(Suspendable.asSingleFqName())) return super.visitFunction(declaration)

        context.irBuiltIns.createIrBuilder(
            declaration.symbol,
            declaration.startOffset,
            declaration.endOffset
        ).apply {
            with(FunctionTransformer) {
                transformFunction(declaration)
            }
        }

        return super.visitFunction(declaration)
    }
}