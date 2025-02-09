package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.backend.wasm.ir2wasm.LocationType
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fileEntry
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.transformers.*

class SuspendCallTransformer(
    val whenContext: WhenContext,
    val controllers: ArrayList<IrVariable>,
) : IrElementTransformerVoid() {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val asyncControllers = pluginContext.referenceClass(SuspendContext)!!.getPropertyGetter("asyncControllers")!!
    val asyncContext = pluginContext.referenceClass(AsyncContext)!!
    val asyncController = pluginContext.referenceClass(AsyncController)!!
    val asyncStart = asyncController.functionByName("start")
    val asyncResume = asyncController.functionByName("start")
    val asyncStop = asyncController.functionByName("stop")
    val asyncPause = asyncController.functionByName("pause")
    val asyncJoin = asyncController.functionByName("join")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val asyncIsEnd = asyncController.getPropertyGetter("isEnd")!!
    val HashSetAdd = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashSet")))!!
        .functions.single { it.owner.valueParameters.size == 1 && it.owner.name.identifier == "add" }
    val HashSetRemove = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashSet")))!!
        .functions.single { it.owner.valueParameters.size == 1 && it.owner.name.identifier == "remove" }
    val await = pluginContext.referenceFunctions(
        CallableId(
            FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"),
            Name.identifier("await")
        )
    ).first()

    override fun visitBody(body: IrBody): IrBody {
        body.statements.forEach(::transformStatement)
        with(whenContext) {
            with(builder) {
                whenStatement.branches += irElseBranch(throwIllegalStateException("Invalid index: ${whenStatement.branches.size}"))
            }
        }
        return body
    }

    fun transformStatement(statement: IrStatement, shouldReplace: Boolean = false): IrExpression? {
        return when (statement) {
            is IrCall -> transformCall(statement, shouldReplace)
            is IrContainerExpression -> transformContainer(statement, shouldReplace)
            is IrLoop -> transformLoop(statement)
            is IrReturn -> transformReturn(statement)
            is IrTypeOperatorCall -> transformStatement(statement.argument, shouldReplace)
            is IrGetValue -> transformGet(statement)
            is IrSetValue -> transformStatement(statement.value, shouldReplace)
            is IrWhen -> transformWhen(statement)
            is IrVariable -> transformVariable(statement)
            is IrThrow -> transformThrow(statement)
            is IrConstructorCall -> transformConstructor(statement)
            is IrStringConcatenation -> transformString(statement)

            is IrConst, is IrGetField, is IrGetSingletonValue, is IrFunctionExpression, is IrClassReference -> return null
            else -> error("Unexpected statement $statement")
        }
    }
}

fun IrBuilderWithScope.throwIllegalStateException(message: String): IrExpression {
    val exceptionClass = context.irBuiltIns.illegalArgumentExceptionSymbol

    val exceptionConstructorCall = irCall(exceptionClass).apply {
        putValueArgument(0, irString(message))
    }

    return irThrow(exceptionConstructorCall)
}

fun IrBuilderWithScope.irIfThenElse(
    type: IrType,
    condition: IrExpression,
    thenPart: IrExpression,
    elsePart: IrExpression,
    origin: IrStatementOrigin? = null,
) = IrWhenImpl(startOffset, endOffset, type, origin).apply {
    branches.add(IrBranchImpl(startOffset, endOffset, condition, thenPart))
    branches.add(irElseBranch(elsePart))
}