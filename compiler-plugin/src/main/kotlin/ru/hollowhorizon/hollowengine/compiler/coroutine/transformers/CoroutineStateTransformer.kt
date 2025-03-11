package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineClassGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.transformBody
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.throwIllegalStateException
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class CoroutineFunctionTransformer(private val functionToClass: HashMap<IrFunction, CoroutineClassGenerator.CoroutineInfo>) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isSuspendable()) {
            val builder = declaration.builder()
            IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, pluginContext.irBuiltIns.unitType, IrStatementOrigin.WHEN, mutableListOf())
            val whenStatement = builder.irWhen(pluginContext.irBuiltIns.unitType, mutableListOf())
            val stateVar = IrVariableImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                IrDeclarationOrigin.DEFINED,
                IrVariableSymbolImpl(),
                Name.special("<stateIndex>"),
                pluginContext.irBuiltIns.intType,
                isVar = false,
                isConst = false,
                isLateinit = false
            )
            stateVar.initializer = builder.irInt(0)
            stateVar.parent = declaration
            val context = WhenContext(builder, stateVar, whenStatement, 0, functionToClass, declaration)
            declaration.body?.let { context.transformBody(it) }
            whenStatement.branches += builder.run { irElseBranch(throwIllegalStateException("Invalid index!")) }

            declaration.body = builder.irBlockBody {
                +stateVar
                +whenStatement
            }
        }
        return super.visitFunction(declaration)
    }
}