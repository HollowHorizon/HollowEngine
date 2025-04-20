package ru.hollowhorizon.hollowengine.compiler.script

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
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
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameter
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class ScriptRelocator : IrElementTransformerVoid() {
    private var setters = HashMap<IrFunction, IrVariable>()
    private var getters = HashMap<IrFunction, IrVariable>()

    var hasScript = false
    lateinit var function: IrFunction

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitClass(declaration: IrClass): IrStatement {
        if (declaration.origin != IrDeclarationOrigin.SCRIPT_CLASS) return super.visitClass(declaration)
        val fileName = (declaration.parent as? IrFile)?.name ?: return super.visitClass(declaration)
        if (!fileName.endsWith(".story.kts")) return super.visitClass(declaration)

        val builder = declaration.builder()
        val storyEvent = declaration.superClass ?: error("StoryEvent class not found!")
        val original = storyEvent.getSimpleFunction("invoke")!!

        hasScript = true

        this.function = pluginContext.irFactory.createSimpleFunction(
            declaration.startOffset, declaration.endOffset, IrDeclarationOrigin.DEFINED,
            Name.identifier("invoke"), original.owner.visibility,
            isInline = false,
            isExpect = false,
            returnType = pluginContext.irBuiltIns.anyNType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = false,
            isOperator = false,
            isInfix = false
        ).apply {
            annotations += builder.irCall(pluginContext.referenceClass(Suspendable)!!.constructors.first())

            parent = declaration
            createDispatchReceiverParameter()
            overriddenSymbols = listOf(original)

            body = builder.irBlockBody {
                declaration.declarations.removeIf {
                    when (it) {
                        is IrAnonymousInitializer -> {
                            it.body.statements.forEach { stmt ->
                                +stmt
                            }
                            return@removeIf true
                        }

                        is IrProperty -> {
                            +it
                            return@removeIf true
                        }
                    }
                    return@removeIf false
                }
            }
        }

        declaration.declarations.add(function)

        return super.visitClass(declaration)
    }

    override fun visitProperty(declaration: IrProperty): IrStatement {
        if (hasScript) {
            declaration.backingField?.let { field ->
                return super.visitVariable(IrVariableImpl(
                    declaration.startOffset,
                    declaration.endOffset,
                    IrDeclarationOrigin.DEFINED,
                    IrVariableSymbolImpl(),
                    field.name,
                    field.type,
                    isVar = true,
                    isConst = false,
                    isLateinit = false
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

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        val builder = pluginContext.irBuiltIns.createIrBuilder(
            expression.symbol,
            expression.startOffset,
            expression.endOffset
        )
        if (hasScript) {
            (expression.dispatchReceiver as? IrValueAccessExpression)?.origin?.let {
                if (it == IrStatementOrigin.IMPLICIT_ARGUMENT) {
                    expression.dispatchReceiver = builder.irGet(function.dispatchReceiverParameter!!)
                }
            }
        }

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