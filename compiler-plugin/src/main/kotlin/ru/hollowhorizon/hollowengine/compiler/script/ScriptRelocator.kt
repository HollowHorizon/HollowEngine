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
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
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
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.isIgnored

class ScriptRelocator() : IrElementTransformerVoid() {
    private var setters = HashMap<IrFunction, IrVariable>()
    private var getters = HashMap<IrFunction, IrVariable>()

    var hasScript = false
    lateinit var function: IrFunction

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitScript(script: IrScript): IrStatement {
        if(!script.name.asStringStripSpecialMarkers().endsWith(".story.kts")) return super.visitScript(script)
        hasScript = true
        val builder = pluginContext.irBuiltIns.createIrBuilder(
            script.symbol,
            script.startOffset,
            script.endOffset
        )

        val storyEvent = script.baseClass!!.getClass()!!
        val original = storyEvent.functions.first { it.name.asString() == "tick" }

        val functions = mutableListOf<IrFunction>()

        this.function = pluginContext.irFactory.createSimpleFunction(
            script.startOffset, script.endOffset, script.origin,
            Name.identifier("tick"), DescriptorVisibilities.PUBLIC,
            isInline = false,
            isExpect = false,
            returnType = script.resultProperty?.owner?.getter?.returnType ?: pluginContext.irBuiltIns.unitType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = false,
            isOperator = false,
            isInfix = false
        ).apply {
            annotations += builder.irCall(pluginContext.referenceClass(Suspendable)!!.constructors.first())

            script.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
                    if (declaration.parent == script && declaration.origin != IrDeclarationOrigin.INSTANCE_RECEIVER) {
                        declaration.parent = this@apply
                    }
//                    if(declaration.origin == IrDeclarationOrigin.INSTANCE_RECEIVER) {
//                        println(declaration)
//                    }
                    return super.visitDeclaration(declaration)
                }

                override fun visitFunction(declaration: IrFunction): IrStatement {
                    if (declaration.parent == script && declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
                        declaration.parent = this@apply
                    }
                    return super.visitFunction(declaration)
                }
            })
            parent = script
            overrides(original)

            body = builder.irBlockBody {
                script.statements.forEach {
                    if(it is IrFunction) functions += it
                    else +it
                }
            }
            script.statements.clear()
        }
        script.statements += functions
        script.statements += function

        return super.visitScript(script)
    }

    override fun visitProperty(declaration: IrProperty): IrStatement {
        if (!declaration.isIgnored() && hasScript) {
            declaration.backingField?.let { field ->
                return super.visitVariable(IrVariableImpl(
                    declaration.startOffset, declaration.endOffset, IrDeclarationOrigin.DEFINED,
                    IrVariableSymbolImpl(), field.name, field.type, isVar = true, isConst = false, isLateinit = false
                ).apply {
                    initializer = field.initializer?.expression
                    parent = function

                    declaration.setter?.let { setters[it] = this }
                    declaration.getter?.let { getters[it] = this }
                })
            }
        }
        return super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val builder = pluginContext.irBuiltIns.createIrBuilder(
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