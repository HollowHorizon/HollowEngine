package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.util.parentAsClass
import ru.hollowhorizon.hollowengine.compiler.JvmHacks
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.IrNothing
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.resumeObject
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.suspendObject
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class WhenContext(
    val generator: CoroutineGenerator,
    val builder: DeclarationIrBuilder,
    val stateVar: IrVariable,
    internal val whenStatement: IrWhen,
    var nextBranch: Int = 0,
    val functionToClass: HashMap<IrFunction, CoroutineGenerator>,
) {
    var innerCallId = HashMap<IrFunction, Int>()
    var whenResultId = 0

    init {
        whenStatement.branches += with(builder) {
            IrBranchImpl(-1, -1, IrCallImpl(
                startOffset = -1, endOffset = -1,
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

    fun append(call: IrStatement) {
        if (call == IrNothing) return
        (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.add(call)
    }

    fun removeLastStatement(): IrStatement? =
        (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.removeLastOrNull()

    fun isBranchEmpty() =
        nextBranch == 0 || (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.isEmpty()

    fun nextBranch(skipInc: Boolean = false, resume: Boolean = false, suspend: Boolean = false) {
        if (isBranchEmpty()) return

        if (!skipInc) {
            append(builder.run { irSet(stateVar, irInt(nextBranch)) })
        }
        if (resume) {
            append(builder.run { irReturn(irGetObject(resumeObject)) })
        }
        if(suspend) {
            append(builder.run { irReturn(irGetObject(suspendObject)) })
        }

        whenStatement.branches += with(builder) {
            IrBranchImpl(-1, -1, IrCallImpl(
                startOffset = -1, endOffset = -1,
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

    val coroutine get() = (builder.parent as IrFunction).parentAsClass
}

