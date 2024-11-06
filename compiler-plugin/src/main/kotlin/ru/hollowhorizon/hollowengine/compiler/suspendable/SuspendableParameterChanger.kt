package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class SuspendableParameterChanger : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.annotations.hasAnnotation(Suspendable.asSingleFqName())) return super.visitFunction(
            declaration
        )

        val type = pluginContext.referenceClass(SuspendContext)!!.defaultType
        declaration.addValueParameter("suspendContext", type)
        declaration.returnType = pluginContext.irBuiltIns.anyNType

        return super.visitFunction(declaration)
    }
}