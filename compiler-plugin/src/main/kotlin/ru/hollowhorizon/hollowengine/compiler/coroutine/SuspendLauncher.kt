package ru.hollowhorizon.hollowengine.compiler.coroutine

import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import ru.hollowhorizon.hollowengine.scripting.SuspendState

class SuspendLauncher(val action: SFunction0<Any?>) {
    fun update(): Any? {
        var result: Any?
        do {
            action.updateAsyncs()

            do {
                result = action()
            } while (result == ResumeState)
        } while (result == SuspendState)
        return result
    }
}

