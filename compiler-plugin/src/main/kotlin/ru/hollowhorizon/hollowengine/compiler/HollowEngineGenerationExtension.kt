package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.PropertyTransformer

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        FunctionTransformer.ctx = pluginContext

        moduleFragment.transform(object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (!declaration.annotations.hasAnnotation(Suspendable)) return super.visitFunction(declaration)
                declaration.addValueParameter(
                    "suspendContext",
                    FunctionTransformer.ctx.referenceClass(SuspendContext)!!.defaultType
                )

                return super.visitFunction(declaration)
            }
        }, null)
        
        moduleFragment.transform(FunctionVisitor(pluginContext), null)
        moduleFragment.transform(CallVisitor(pluginContext), null)
    }
}