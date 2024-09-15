package ru.hollowhorizon.hollowengine.compiler.story

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

/**
 * Транстформирует скрипт или функции таким образом, чтобы они могли быть сериализуемы.
 */
class StoryEventTransformer(private val context: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
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