package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer

/**
 * Трансформирует скрипт или функции таким образом, чтобы они могли быть сериализуемы.
 */
class FunctionVisitor(private val context: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.annotations.hasAnnotation(Suspendable)) return super.visitFunction(declaration)
        declaration.addValueParameter(
            "suspendContext",
            FunctionTransformer.ctx.referenceClass(SuspendContext)!!.defaultType
        )

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