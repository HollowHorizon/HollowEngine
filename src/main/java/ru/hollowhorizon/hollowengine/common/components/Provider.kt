package ru.hollowhorizon.hollowengine.common.components

import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

fun <T : Any> generateProvider(clazz: Class<T>): () -> T {
    val lookup = MethodHandles.lookup()

    val ctor = lookup.findConstructor(clazz, MethodType.methodType(Void.TYPE))

    val callSite = LambdaMetafactory.metafactory(
        lookup,
        "invoke",
        MethodType.methodType(Function0::class.java),
        MethodType.methodType(Object::class.java),
        ctor,
        MethodType.methodType(clazz)
    )

    return callSite.target.invokeExact() as () -> T
}