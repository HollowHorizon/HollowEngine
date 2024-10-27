package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.overrides
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.transformFunction

/**
 * Трансформирует скрипт или функции таким образом, чтобы они могли быть сериализуемы.
 */
class FunctionVisitor(private val context: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.annotations.hasAnnotation(Suspendable)) return super.visitFunction(declaration)

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

    override fun visitScript(script: IrScript): IrStatement {
        val builder = context.irBuiltIns.createIrBuilder(
            script.symbol,
            script.startOffset,
            script.endOffset
        )

        val storyEvent = script.baseClass!!.getClass()!!
        val original = storyEvent.functions.first { it.name.asString() == "tick" }

        val function = context.irFactory.createSimpleFunction(
            script.startOffset, script.endOffset, script.origin,
            Name.identifier("tick"), DescriptorVisibilities.PUBLIC,
            isInline = false,
            isExpect = false,
            returnType = context.irBuiltIns.unitType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = false,
            isOperator = false,
            isInfix = false
        ).apply {
            script.transformChildrenVoid(object: IrElementTransformerVoid() {
                override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
                    if(declaration.parent == script && declaration.origin != IrDeclarationOrigin.INSTANCE_RECEIVER) {
                        declaration.parent = this@apply
                    }
                    return super.visitDeclaration(declaration)
                }

                override fun visitFunction(declaration: IrFunction): IrStatement {
                    if(declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA && declaration.parent == script) {
                        declaration.parent = this@apply
                    }
                    return super.visitFunction(declaration)
                }
            })
            parent = script
            overrides(original)

            body = builder.irBlockBody {
                script.statements.forEach {
                    +it
                }
            }
            script.statements.clear()
            addValueParameter("context", context.referenceClass(SuspendContext)!!.defaultType)

            builder.transformFunction(this)
        }
        script.statements += function

        return super.visitScript(script)
    }
}