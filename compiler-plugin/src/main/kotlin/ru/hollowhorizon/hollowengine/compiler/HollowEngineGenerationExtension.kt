package ru.hollowhorizon.hollowengine.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.checkDeclarationParents
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.declarations.builder.FirValueParameterBuilder
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrImplementationDetail
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx
import java.io.File
import kotlin.metadata.jvm.KotlinClassMetadata

class HollowEngineGenerationExtension : IrGenerationExtension {
    @OptIn(IrImplementationDetail::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        ctx = pluginContext

        val transformer = object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (!declaration.annotations.hasAnnotation(Suspendable)) return super.visitFunction(declaration)

                val type = ctx.referenceClass(SuspendContext)!!.defaultType
                val parameter = declaration.addValueParameter(
                    "suspendContext",
                    type
                )
//                (declaration.metadata as? FirMetadataSource.Function)?.let {
//                    it.fir.replaceValueParameters(it.fir.valueParameters + FirValueParameterBuilder().apply {
//                        moduleData = it.fir.moduleData
//                        origin = it.fir.origin
//                        returnTypeRef = buildResolvedTypeRef {
//                            this.type =
//                                ConeClassLikeTypeImpl(SuspendContext.toLookupTag(), emptyArray(), isNullable = false)
//                        }
//                        name = parameter.name
//                        symbol = FirValueParameterSymbol(name)
//                        isCrossinline = parameter.isCrossinline
//                        isVararg = parameter.isVararg
//                        isNoinline = parameter.isNoinline
//                        containingFunctionSymbol = it.fir.symbol
//                    }.build())
//                }

                return super.visitFunction(declaration)
            }
        }

        moduleFragment.transform(transformer, null)
        moduleFragment.transform(FunctionVisitor(pluginContext), null)
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