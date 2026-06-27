package ru.hollowhorizon.hollowengine.common.scripting.components

import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.common.scripting.Every
import ru.hollowhorizon.hollowengine.common.utils.LambdaGenerator
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.MethodHandles

fun ComponentScript.prepareTickers(): () -> Unit {
    val lookup = MethodHandles.privateLookupIn(javaClass, UnsafeTools.lookup)

    val runnables = javaClass.declaredMethods
        .filter { it.annotations.any { it is Every } && it.parameterCount == 0 }
        .map { method ->
            val annotation = method.getAnnotation(Every::class.java)
            assert(annotation.ticks > 0) { "${annotation.ticks} ticks must be > 0" }

            val lambda = LambdaGenerator.createRunnable(lookup, method, this)
            makeTickableRunnable(lambda, annotation.ticks)
        }

    return {
        runnables.forEach { it() }
    }
}

private fun makeTickableRunnable(input: () -> Unit, every: Int): () -> Unit {
    if (every == 1) return input

    return {
        if (TickHandler.serverTicks % every == 0) {
            input()
        }
    }
}