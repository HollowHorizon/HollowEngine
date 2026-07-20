package ru.hollowhorizon.hollowengine.common.scripting.nodes

import ru.hollowhorizon.hollowengine.common.scripting.annotations.State
import ru.hollowhorizon.hollowengine.common.scripting.state.StateExecutor
import ru.hollowhorizon.hollowengine.common.utils.LambdaGenerator
import ru.hollowhorizon.hollowengine.common.utils.UnsafeTools
import java.lang.invoke.MethodHandles

fun NodeScript.prepareExecutor(executor: StateExecutor) {
    val lookup = MethodHandles.privateLookupIn(javaClass, UnsafeTools.lookup)

    javaClass.declaredMethods
        .filter { it.annotations.any { it is State } }
        .forEach { method ->
            val annotation = method.getAnnotation(State::class.java)
            val name = annotation.name.takeIf { it.isNotBlank() } ?: method.name

            executor.addState(name, LambdaGenerator.createSuspendRunnable(lookup, method, this))
        }
}
