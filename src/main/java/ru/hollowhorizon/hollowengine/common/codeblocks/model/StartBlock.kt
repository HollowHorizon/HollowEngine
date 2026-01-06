package ru.hollowhorizon.hollowengine.common.codeblocks.model

import de.fabmax.kool.modules.ui2.MutableStateValue
import de.fabmax.kool.modules.ui2.mutableStateOf

abstract class StartBlock : StatementBlock() {
    val isGlobal: MutableStateValue<Boolean> = mutableStateOf(false)

    abstract suspend fun trigger()

    override suspend fun execute() {
        // При глобальном запуске мы сразу переходим к следующему блоку, при локальном - ждём событие
        if (!isGlobal.value) return

        trigger()
    }
}

fun StartBlock.toggleMode() {
    isGlobal.set(!isGlobal.value)
}