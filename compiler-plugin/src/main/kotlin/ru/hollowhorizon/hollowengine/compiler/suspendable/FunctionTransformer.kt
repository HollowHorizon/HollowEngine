package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import ru.hollowhorizon.hollowengine.compiler.identifiers.SequenceNode
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.StatementsTransformer.transformStatements


object FunctionTransformer {
    lateinit var ctx: IrPluginContext

    fun DeclarationIrBuilder.transformFunction(function: IrFunction) {
        if (!function.annotations.hasAnnotation(Suspendable)) return
        val sequenceNode = ctx.referenceClass(SequenceNode) ?: return

        function.transformChildren(DelegatePropertiesTransformer(), null) // Заменяем все переменные на делегаты
        function.transformChildren(DelegateParametersTransformer(), null)

        function.body = irBlockBody {
            transformStatements(function.body!!.statements)
        }
        function.returnType = sequenceNode.defaultType
    }
}

