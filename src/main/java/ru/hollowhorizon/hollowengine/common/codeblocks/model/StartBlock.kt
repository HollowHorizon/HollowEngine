package ru.hollowhorizon.hollowengine.common.codeblocks.model

import de.fabmax.kool.modules.ui2.MutableStateValue

interface StartBlock {
    val mode: MutableStateValue<TriggerMode>
}

fun StartBlock.toggleMode() {
    mode.set(if(mode.value == TriggerMode.LOCAL) TriggerMode.GLOBAL else TriggerMode.LOCAL)
}

enum class TriggerMode {
    LOCAL, GLOBAL;

    fun isGlobal() = this == GLOBAL
}