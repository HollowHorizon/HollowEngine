package ru.hollowhorizon.hollowengine.compiler.coroutine

import ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState
import ru.hollowhorizon.hollowengine.scripting.Suspendable

class SuspendLauncher(val action: @Suspendable () -> Any?) {
    fun update() {
        while (action() == ResumeState);
    }
}

class AsyncController(val action: @Suspendable () -> Any?) {
    var isActive = false

    fun update() {
        if (isActive) {
            while (action() == ResumeState);
        }
    }
}