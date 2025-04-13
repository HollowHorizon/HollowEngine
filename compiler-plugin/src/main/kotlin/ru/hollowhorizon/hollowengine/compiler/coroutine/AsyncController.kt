package ru.hollowhorizon.hollowengine.compiler.coroutine

import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import ru.hollowhorizon.hollowengine.scripting.Suspendable

class AsyncController(val action: SFunction0<Any?>) {
    var isActive = false
        private set

    fun update() {
        if (isActive) {
            var result: Any?
            do {
                result = action()
            } while (result == ResumeState)
            if (result != SuspendState) isActive = false
        }
    }

    fun start() {
        isActive = true
    }

    fun stop() {
        isActive = false
    }
}

fun async(function: @Suspendable () -> Unit): AsyncController = error("Must be replaced by compiler!")