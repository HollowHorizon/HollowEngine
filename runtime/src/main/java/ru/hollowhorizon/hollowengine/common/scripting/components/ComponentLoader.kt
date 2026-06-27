package ru.hollowhorizon.hollowengine.common.scripting.components

import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Every
import ru.hollowhorizon.hollowengine.common.scripting.annotations.State
import ru.hollowhorizon.hollowengine.common.scripting.state.StateExecutor
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

fun ComponentScript.prepareExecutor(executor: StateExecutor) {
    val lookup = MethodHandles.privateLookupIn(javaClass, UnsafeTools.lookup)

    javaClass.declaredMethods
        .filter { it.annotations.any { it is State } }
        .forEach { method ->
            val annotation = method.getAnnotation(State::class.java)
            val name = annotation.name.takeIf { it.isNotBlank() } ?: method.name

            executor.addState(name, LambdaGenerator.createSuspendRunnable(lookup, method, this))
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