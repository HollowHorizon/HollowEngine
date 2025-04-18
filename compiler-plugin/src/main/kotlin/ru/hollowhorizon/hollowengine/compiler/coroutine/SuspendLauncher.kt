package ru.hollowhorizon.hollowengine.compiler.coroutine

import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState

class SuspendLauncher(val action: SFunction0<Any?>) {
    fun update(): Any? {
        action.updateAsyncs()
        var result: Any?
        do {
            result = action()
        } while (result == ResumeState || result == SuspendState)
        return result
    }
}

