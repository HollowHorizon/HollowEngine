package ru.hollowhorizon.hollowengine.compiler.coroutine

import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.ResumeState

class SuspendLauncher(val action: SFunction0<Any?>) {
    fun update(): Any? {
        var result: Any?
        do {
            result = action()
        } while (result == ResumeState)
        return result
    }
}

