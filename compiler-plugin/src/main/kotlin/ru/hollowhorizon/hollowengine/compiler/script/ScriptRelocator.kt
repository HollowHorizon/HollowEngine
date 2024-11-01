package ru.hollowhorizon.hollowengine.compiler.script

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.overrides
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx
import ru.hollowhorizon.hollowengine.compiler.suspendable.isIgnored

class ScriptRelocator(val context: IrPluginContext) : IrElementTransformerVoid() {
    private var setters = HashMap<IrFunction, IrVariable>()
    private var getters = HashMap<IrFunction, IrVariable>()

    var hasScript = false
    lateinit var function: IrFunction

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitScript(declaration: IrScript): IrStatement {
        if(!declaration.name.asStringStripSpecialMarkers().endsWith(".story.kts")) return super.visitScript(declaration)
        hasScript = true
        val builder = context.irBuiltIns.createIrBuilder(
            declaration.symbol,
            declaration.startOffset,
            declaration.endOffset
        )

        val storyEvent = declaration.baseClass!!.getClass()!!
        val original = storyEvent.functions.first { it.name.asString() == "tick" }

        this.function = context.irFactory.createSimpleFunction(
            declaration.startOffset, declaration.endOffset, declaration.origin,
            Name.identifier("tick"), DescriptorVisibilities.PUBLIC,
            isInline = false,
            isExpect = false,
            returnType = declaration.resultProperty?.owner?.getter?.returnType ?: context.irBuiltIns.unitType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = false,
            isOperator = false,
            isInfix = false
        ).apply {
            annotations += builder.irCall(ctx.referenceClass(Suspendable)!!.constructors.first())

            declaration.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
                    if (declaration.parent == declaration && declaration.origin != IrDeclarationOrigin.INSTANCE_RECEIVER) {
                        declaration.parent = this@apply
                    }
                    return super.visitDeclaration(declaration)
                }

                override fun visitFunction(declaration: IrFunction): IrStatement {
                    if (declaration.parent == declaration && declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
                        declaration.parent = this@apply
                    }
                    return super.visitFunction(declaration)
                }
            })
            parent = declaration
            overrides(original)

            body = builder.irBlockBody {
                declaration.statements.forEach {
                    +it
                }
            }
            declaration.statements.clear()
        }
        declaration.statements += function

        return super.visitScript(declaration)
    }

    override fun visitProperty(declaration: IrProperty): IrStatement {
        if (!declaration.isIgnored() && hasScript) {
            declaration.backingField?.let { field ->
                return IrVariableImpl(
                    declaration.startOffset, declaration.endOffset, IrDeclarationOrigin.DEFINED,
                    IrVariableSymbolImpl(), field.name, field.type, isVar = true, isConst = false, isLateinit = false
                ).apply {
                    initializer = field.initializer?.expression
                    parent = function

                    declaration.setter?.let { setters[it] = this }
                    declaration.getter?.let { getters[it] = this }
                }
            }
        }
        return super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val builder = context.irBuiltIns.createIrBuilder(
            expression.symbol,
            expression.startOffset,
            expression.endOffset
        )

        var result: IrExpression = expression

        setters[expression.symbol.owner]?.let {
            result = builder.irSet(it, expression.getValueArgument(0)!!)
        }
        getters[expression.symbol.owner]?.let {
            result = builder.irGet(it)
        }

        return super.visitExpression(result)
    }
}