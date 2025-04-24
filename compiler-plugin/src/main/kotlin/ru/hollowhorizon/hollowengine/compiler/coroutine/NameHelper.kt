package ru.hollowhorizon.hollowengine.compiler.coroutine

import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.isAnonymous
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull

object NameHelper {
    private const val SEPARATOR = "_"
    private val anonymousIndexes = HashMap<IrClass, Int>()

    fun createName(function: IrFunction) = if (function.name.isAnonymous) {
        val parentClass = function.parentClassOrNull ?: error("parent class not found")
        val index = anonymousIndexes.getOrPut(parentClass) { 0 }
        anonymousIndexes[parentClass] = index + 1
        "Lambda${SEPARATOR}$index"
    } else {
        val parameterTypes = function.valueParameters.joinToString(SEPARATOR) { param ->
            param.type.toJvmDescriptor()
        }
        function.name.identifier + parameterTypes + "${SEPARATOR}SerializableCoroutine"
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrType.toJvmDescriptor(): String {
        return when (val classifier = this.classifierOrNull) {
            is IrClassSymbol -> mapClassToDescriptor(classifier.owner)
            is IrTypeParameterSymbol -> "T" // Обобщённый тип (условно)
            else -> "java${SEPARATOR}lang${SEPARATOR}Object"     // fallback
        }
    }

    private fun mapClassToDescriptor(clazz: IrClass): String {
        return when (clazz.fqNameWhenAvailable?.asString()) {
            "kotlin.Int" -> "I"
            "kotlin.Long" -> "J"
            "kotlin.Short" -> "S"
            "kotlin.Byte" -> "B"
            "kotlin.Boolean" -> "Z"
            "kotlin.Float" -> "F"
            "kotlin.Double" -> "D"
            "kotlin.Char" -> "C"
            "kotlin.Unit" -> "V"
            else -> {
                clazz.fqNameWhenAvailable?.asString()?.replace(".", SEPARATOR) ?: "java${SEPARATOR}lang${SEPARATOR}Object"
            }
        }
    }
}