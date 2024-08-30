package ru.hollowhorizon.hollowengine.common.scripting.compiler.story

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.common.scripting.compiler.story.StatementsTransformer.transformStatements

@OptIn(UnsafeDuringIrConstructionAPI::class)

object FunctionTransformer {
    lateinit var ctx: IrPluginContext

    fun DeclarationIrBuilder.transformFunction(function: IrFunction) {
        val builder =
            ctx.irBuiltIns.createIrBuilder(function.symbol, function.startOffset, function.endOffset)

        val delegateType = ctx.referenceClass(
            ClassId(
                FqName("ru.hollowhorizon.hollowengine.common.scripting.compiler.story"),
                Name.identifier("PropertyDelegate")
            )
        )!!

        if (!function.annotations.hasAnnotation(FqName("ru.hollowhorizon.hollowengine.common.scripting.story.StoryFunction"))) return
        val sequenceNode = ctx.referenceClass(
            ClassId(
                FqName("ru.hollowhorizon.hollowengine.common.scripting.story.nodes"),
                Name.identifier("SequenceNode")
            )
        ) ?: return

        val transformer = LocalPropertiesTransformer()
        function.transformChildren(transformer, null)

        function.transformChildren(FunctionPropertiesTransformer(), null)

        function.body = irBlockBody {
            transformStatements(function.body!!.statements)
        }
        function.returnType = sequenceNode.defaultType
    }


}

