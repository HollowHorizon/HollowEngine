package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrImplementationDetail
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.*
import ru.hollowhorizon.hollowengine.compiler.identifiers.ArrayList
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendableTransformer
import java.io.File
import kotlin.metadata.jvm.KotlinClassMetadata

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        ctx = pluginContext

        val transformer = object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (!declaration.annotations.hasAnnotation(Suspendable.asSingleFqName())) return super.visitFunction(declaration)

                val type = ctx.referenceClass(SuspendContext)!!.defaultType
                declaration.addValueParameter("suspendContext", type)
                declaration.returnType = pluginContext.irBuiltIns.anyNType

                return super.visitFunction(declaration)
            }
        }

        moduleFragment.transform(ScriptRelocator(pluginContext), null)
        moduleFragment.transform(transformer, null)
        moduleFragment.transform(SuspendableTransformer(pluginContext), null)
        moduleFragment.transform(CallVisitor(pluginContext), null)

        moduleFragment.checkDeclarationParents()
    }

}

fun FileLoweringPass.runOnFileInOrder(irFile: IrFile) {
    irFile.acceptVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitFile(declaration: IrFile) {
            lower(declaration)
            declaration.acceptChildrenVoid(this)
        }
    })
}

fun main() {
    class Loader : ClassLoader() {
        override fun findClass(name: String?): Class<*> {
            if (name == "data") {
                val bytes = File("C:\\Users\\Artem\\Downloads\\NPCActionsKt.class").readBytes()
                return defineClass(
                    "ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.NPCActionsKt",
                    bytes,
                    0,
                    bytes.size
                )
            }
            return super.findClass(name)
        }
    }

    val data = KotlinClassMetadata.readStrict(Loader().loadClass("data").getAnnotation(Metadata::class.java))

    println(data)
}