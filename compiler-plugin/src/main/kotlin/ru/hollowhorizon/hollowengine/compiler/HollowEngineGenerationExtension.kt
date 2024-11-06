package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.*
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.script.ScriptRelocator
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendableParameterChanger
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendableTransformer
import java.io.File
import kotlin.metadata.jvm.KotlinClassMetadata

lateinit var pluginContext: IrPluginContext

class HollowEngineGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, context: IrPluginContext) {
        pluginContext = context

        moduleFragment.transform(ScriptRelocator(), null)
        moduleFragment.transform(SuspendableParameterChanger(), null)
        moduleFragment.transform(SuspendableTransformer(), null)
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