package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx

class WhenContext(
    val builder: DeclarationIrBuilder,
    val whenStatement: IrWhen,
    val stateVar: IrExpression,
    var suspendContext: IrValueParameter,
    var context: IrPluginContext,
    var nextBranch: Int = 0,
) {
    val suspendContextSymbol = context.referenceClass(SuspendContext)!!
    val setter = suspendContextSymbol.functionByName("setProperty")
    val getter = suspendContextSymbol.functionByName("getProperty")
    val remover = suspendContextSymbol.functionByName("removeProperty")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val suspendGetter = context.referenceClass(SuspendContext)!!.getPropertyGetter("index")!!

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val suspendSetter = context.referenceClass(SuspendContext)!!.getPropertySetter("index")!!


    init {
        nextBranch()
    }

    fun append(call: IrExpression) {
        (whenStatement.branches[nextBranch - 1].result as IrBlock).statements.add(call)
    }

    fun nextBranch() {
        whenStatement.branches += with(builder) { irBranch(irEqeqeq(stateVar, irInt(nextBranch++)), irBlock {}) }
    }

    fun switchState() {
        append(with(builder) {
            irCall(suspendSetter).apply {
                dispatchReceiver = irGet(suspendContext)
                putValueArgument(0, irInt(nextBranch))
            }
        })
    }

    fun returnResume() {
        append(builder.irReturn(builder.irGetObject(ctx.referenceClass(ResumeState)!!)))
    }

    fun returnSuspend() {
        append(builder.irReturn(builder.irGetObject(ctx.referenceClass(SuspendState)!!)))
    }
}