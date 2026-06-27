package ru.hollowhorizon.hollowengine.common.utils

import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method

object LambdaGenerator {
    fun createRunnable(lookup: MethodHandles.Lookup, method: Method, target: Any): () -> Unit {
        val methodHandle = lookup.unreflect(method)

        val samMethodType = MethodType.methodType(Void.TYPE)
        val factoryType = MethodType.methodType(Runnable::class.java, target::class.java)
        val instantiatedMethodType = MethodType.methodType(Void.TYPE)

        val callSite = LambdaMetafactory.metafactory(
            lookup,
            "run",
            factoryType,
            samMethodType,
            methodHandle,
            instantiatedMethodType,
        )
        val runnable = callSite.target.invoke(target) as Runnable
        return runnable::run
    }
}