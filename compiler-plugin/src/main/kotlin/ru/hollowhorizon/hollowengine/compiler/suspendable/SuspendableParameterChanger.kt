package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class SuspendableParameterChanger : IrElementTransformerVoid() {
    val SuspendContext = pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext)!!

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.isSuspendable()) return super.visitFunction(declaration)

        val builder = pluginContext.irBuiltIns.createIrBuilder(declaration.symbol)

        if (declaration.annotations.hasAnnotation(Suspendable.asSingleFqName())) {
            declaration.addValueParameter("suspendContext", SuspendContext.defaultType)
        } else { // Предположительно это уже готовая лямбда
            declaration.annotations += builder.irCall(SuspendContext.constructors.first())
        }
        declaration.returnType = pluginContext.irBuiltIns.anyNType

        return super.visitFunction(declaration)
    }
}