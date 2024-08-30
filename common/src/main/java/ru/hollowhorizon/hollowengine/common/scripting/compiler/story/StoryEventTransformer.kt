package ru.hollowhorizon.hollowengine.common.scripting.compiler.story

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.HollowScript
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.SequenceNode
import java.io.File
import kotlin.script.experimental.api.valueOrThrow

class StoryEventTransformer(val context: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        val builder =
            context.irBuiltIns.createIrBuilder(declaration.symbol, declaration.startOffset, declaration.endOffset)


        with(builder) {
            with(FunctionTransformer) {
                transformFunction(declaration)
            }
        }
        return super.visitFunction(declaration)
    }
}

suspend fun main() {
    val old = File("script.kts.jar")
    if (old.exists()) old.delete()
    val script = ScriptingCompiler.compileFile<HollowScript>(File("script.kts"))

    val result = script.execute {}

    val instance = result.valueOrThrow().returnValue.scriptInstance!!

    val test = instance::class.java.declaredMethods.find { it.name == "test" }

    val r = test?.invoke(instance) as SequenceNode

    println(r)

}