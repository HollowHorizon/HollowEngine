package ru.hollowhorizon.hollowengine.common.scripting.core.completion.util

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.nullability

fun <TCallable : CallableDescriptor> TCallable.substituteExtensionIfCallable(
    receiverTypes: Collection<KotlinType>,
    callType: CallType<*>,
): Collection<TCallable> {
    if (!callType.descriptorKindFilter.accepts(this)) return emptyList()

    var types = receiverTypes.asSequence()
    if (callType == CallType.SAFE) {
        types = types.map { it.makeNotNullable() }
    }

    val extensionReceiverType = fuzzyExtensionReceiverType()!!
    val substitutes = types.mapNotNull {
        var substitutor = extensionReceiverType.checkIsSuperTypeOf(it)
        // check if we may fail due to receiver expression being nullable
        if (substitutor == null && it.nullability() == TypeNullability.NULLABLE && extensionReceiverType.nullability() == TypeNullability.NOT_NULL) {
            substitutor = extensionReceiverType.checkIsSuperTypeOf(it.makeNotNullable())
        }
        substitutor
    }
    return if (typeParameters.isEmpty()) { // optimization for non-generic callables
        if (substitutes.any()) listOf(this) else emptyList()
    } else {
        substitutes
            .mapNotNull { @Suppress("UNCHECKED_CAST") (substitute(it) as TCallable?) }
            .toList()
    }
}