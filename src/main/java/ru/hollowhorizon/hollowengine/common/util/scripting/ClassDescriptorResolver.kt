package ru.hollowhorizon.hollowengine.common.util.scripting

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.resolve.annotations.hasJvmStaticAnnotation
import org.jetbrains.kotlin.resolve.jvm.annotations.hasJvmFieldAnnotation
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeUtils

data class MethodDescriptor(val name: String, val parameters: List<String>, val returnType: String) {
    override fun toString(): String {
        return "$name(${parameters.joinToString(", ")}) -> $returnType"
    }
}

data class FieldDescriptor(val name: String, val returnType: String) {
    override fun toString(): String {
        return "$name -> $returnType"
    }
}

val KotlinType.simpleClassName: String
    get() {
        return when (this) {
            is SimpleType -> {
                return constructor.declarationDescriptor?.name?.asString()
                    ?: nameIfStandardType?.asString()
                    ?: "Unit"
            }

            is FlexibleType -> lowerBound.simpleClassName
            else -> TypeUtils.getClassDescriptor(this)?.name?.asString() ?: "???"
        }
    }

fun ClassDescriptor.getMethodsAndVariables(isStatic: Boolean): Pair<List<MethodDescriptor>, List<FieldDescriptor>> {
    val scope = if(isStatic) staticScope else unsubstitutedMemberScope

    val methods = scope.getContributedDescriptors()
        .filterIsInstance<org.jetbrains.kotlin.descriptors.FunctionDescriptor>()
        .filter { it.visibility == DescriptorVisibilities.PUBLIC }
        .map {
            val result = it.returnType?.simpleClassName ?: "???"
            val args = it.valueParameters.map { it.name.asString() + ": " + it.type.simpleClassName }
            MethodDescriptor(it.name.asString(), args, result)
        }

    val variables = scope.getContributedDescriptors()
        .filterIsInstance<org.jetbrains.kotlin.descriptors.PropertyDescriptor>()
        .filter { it.visibility == DescriptorVisibilities.PUBLIC }
        .map {
            val result = it.returnType?.simpleClassName ?: "???"
            FieldDescriptor(it.name.asString(), result)
        }

    return Pair(methods, variables)
}