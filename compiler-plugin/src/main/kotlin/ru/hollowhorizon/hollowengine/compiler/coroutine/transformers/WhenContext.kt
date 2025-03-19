package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers

import JvmHacks
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineClassGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.IrNothing
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class WhenContext(
    val builder: DeclarationIrBuilder,
    val stateVar: IrVariable,
    private val whenStatement: IrWhen,
    var nextBranch: Int = 0,
    val functionToClass: HashMap<IrFunction, Pair<IrClass, CoroutineClassGenerator.SerializerInfo>>,
    val self: IrFunction,
) {
    var innerCallId = 0

    init {
        nextBranch(true)
    }

    fun append(call: IrStatement) {
        if (call == IrNothing) return
        (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.add(call)
    }

    fun removeLastStmt() = (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.removeLast()
    fun isBranchEmpty() = (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.isEmpty()

    fun nextBranch(skipInc: Boolean = false) {
        if (!skipInc) {
            append(builder.run { irSet(stateVar, irInt(nextBranch)) })
        }

        whenStatement.branches += with(builder) {
            IrBranchWatchable(-1, -1, IrCallWatchable(
                symbol = pluginContext.irBuiltIns.eqeqSymbol,
                type = pluginContext.irBuiltIns.booleanType,
                origin = IrStatementOrigin.EQEQ
            ).apply {
                JvmHacks.initializeTargetShapeFromSymbol(this)
                JvmHacks.initializeEmptyTypeArguments(this)
                arguments[0] = irGet(stateVar)
                arguments[1] = irInt(nextBranch++)
            }, irBlock {})
        }
    }
}

class IrCallWatchable internal constructor(
    override val startOffset: Int = -1,
    override val endOffset: Int = -1,
    override var type: IrType,
    override var origin: IrStatementOrigin? = null,
    symbol: IrSimpleFunctionSymbol,
    override var superQualifierSymbol: IrClassSymbol? = null,
) : IrCall() {
    init {
        val args = arguments
    }

    override var attributeOwnerId: IrElement = this

    override val typeArguments: MutableList<IrType?> = ArrayList(0)

    override var symbol: IrSimpleFunctionSymbol = symbol
        set(value) {
            if (field !== value) {
                field = value
                updateTargetSymbol()
            }
        }

    companion object
}

class IrBranchWatchable internal constructor(
    override val startOffset: Int,
    override val endOffset: Int,
    var condition1: IrExpression,
    override var result: IrExpression,
) : IrBranch() {
    override var attributeOwnerId: IrElement = this

    var lastCatch: Array<out StackTraceElement>? = null

    override var condition: IrExpression
        get() {
            ((condition1 as? IrCallWatchable)?.arguments?.getOrNull(0) as? IrGetField)?.let {
                println("Hey!")
            }

            try {
                error("Ignore")
            } catch (e: Exception) {
                lastCatch = e.stackTrace
            }
            return condition1
        }
        set(v) {
            condition1 = v
        }
}