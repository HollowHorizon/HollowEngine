package ru.hollowhorizon.hc.common.utils.molang.compiler

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import ru.hollowhorizon.hc.common.utils.molang.runtime.Math
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

fun sin(x: Float) = kotlin.math.sin(x)

object MolangFunctions {
    private data class InternalSignature(val className: String, val argCount: Int)
    data class Signature(val className: String, val methodName: String, val returnType: String, val argCount: Int, val isStatic: Boolean)

    private val MATH = "ru/hollowhorizon/hc/common/utils/molang/runtime/Math"
    private val functions = Object2ObjectOpenHashMap<InternalSignature, Signature>()

    init {
        Math::class.memberFunctions.forEach {
            register("math.${it.name}", MATH, it.name, "F", it.valueParameters.size, true)
            register(it.name, MATH, it.name, "F", it.valueParameters.size, true)
        }
    }

    fun register(name: String, className: String, functionName: String, returnType: String, argsCount: Int, isStatic: Boolean = true) {
        functions[InternalSignature(name, argsCount)] = Signature(className, functionName, returnType, argsCount, isStatic)
    }

    fun resolve(name: String, size: Int): Signature {
        return functions[InternalSignature(name, size)] ?: error("Function '$name' with '$size' args not found!")
    }

}