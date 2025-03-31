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
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.CoroutineTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.transformBody
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.throwIllegalStateException
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class CoroutineStateTransformer(private val functionToClass: HashMap<IrFunction, CoroutineGenerator>) : CoroutineTransformer() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if(declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) return super.visitFunction(declaration)
        declaration.builder {
            val whenStatement = irWhen(pluginContext.irBuiltIns.unitType, mutableListOf())
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
            stateVar.initializer = irInt(0)
            stateVar.parent = declaration
            val context = WhenContext(this, stateVar, whenStatement, 0, functionToClass)
            declaration.body?.let { context.transformBody(it) }
            whenStatement.branches += irElseBranch(throwIllegalStateException("Invalid index!"))

            declaration.body = irBlockBody {
                +stateVar
                +whenStatement
            }
        }
        return declaration
    }
}