package ru.hollowhorizon.hollowengine.common.plugin.ir.transformers

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import java.util.*

/**
 * Restores dispatch receivers for declarations exposed through transitive script imports.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class ImportedScriptReceiverTransformer(
    module: IrModuleFragment,
    private val context: IrPluginContext,
) : IrElementTransformerVoid() {
    private val scriptClasses = module.files.flatMap { file -> file.declarations.filterIsInstance<IrClass>() }
        .filter { declaration -> declaration.origin == IrDeclarationOrigin.SCRIPT_CLASS }
        .associateBy { declaration -> declaration.symbol }

    private val dependencies = scriptClasses.values.associate { script ->
        script.symbol to script.declarations.filterIsInstance<IrField>()
            .filter { field -> field.origin == IrDeclarationOrigin.SCRIPT_IMPLICIT_RECEIVER }.mapNotNull { field ->
                val target = field.type.classOrNull?.takeIf(scriptClasses::containsKey)
                target?.let { ScriptDependency(it, field) }
            }
    }

    private var currentScript: IrClass? = null
    private val functionStack = ArrayDeque<IrFunction>()

    override fun visitClass(declaration: IrClass): IrStatement {
        val previousScript = currentScript
        if (declaration.symbol in scriptClasses) currentScript = declaration

        val result = super.visitClass(declaration)
        currentScript = previousScript
        return result
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        functionStack.addLast(declaration)
        val result = super.visitFunction(declaration)
        functionStack.removeLast()
        return result
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression) as IrCall
        val receiverParameter =
            transformed.symbol.owner.dispatchReceiverParameter?.takeIf { parameter -> parameter.origin == IrDeclarationOrigin.SCRIPT_THIS_RECEIVER }
                ?: return transformed
        if (transformed.arguments[receiverParameter] != null) return transformed

        val source = currentScript ?: return transformed
        val target = receiverParameter.type.classOrNull ?: return transformed
        val path = findPath(source.symbol, target) ?: return transformed
        val sourceReceiver = currentScriptReceiver(source) ?: return transformed
        for (index in 1 until path.size) {
            path[index].visibility = DescriptorVisibilities.INTERNAL
        }
        val builder = context.irBuiltIns.createIrBuilder(transformed.symbol)
        var receiver: IrExpression = builder.irGet(sourceReceiver)
        path.forEach { field -> receiver = builder.irGetField(receiver, field) }
        transformed.arguments[receiverParameter] = receiver
        return transformed
    }

    private fun currentScriptReceiver(script: IrClass): IrValueParameter? {
        val functions = functionStack.descendingIterator()
        while (functions.hasNext()) {
            functions.next().dispatchReceiverParameter?.let { parameter ->
                if (parameter.type == script.defaultType) return parameter
            }
        }
        return script.thisReceiver
    }

    private fun findPath(source: IrClassSymbol, target: IrClassSymbol): List<IrField>? {
        if (source == target) return emptyList()

        val queue = ArrayDeque<Path>()
        val visited = hashSetOf(source)
        queue.addLast(Path(source, emptyList()))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            dependencies[current.script].orEmpty().forEach { dependency ->
                if (!visited.add(dependency.script)) return@forEach
                val fields = current.fields + dependency.field
                if (dependency.script == target) return fields
                queue.addLast(Path(dependency.script, fields))
            }
        }
        return null
    }

    private data class ScriptDependency(val script: IrClassSymbol, val field: IrField)

    private data class Path(val script: IrClassSymbol, val fields: List<IrField>)
}
