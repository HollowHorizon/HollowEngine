package ru.hollowhorizon.hollowengine.common.codeblocks.model

import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope

interface ContainerBlock {
    fun InputSlotScope.composeBody() {}
}