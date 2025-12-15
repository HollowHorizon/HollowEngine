package ru.hollowhorizon.hollowengine.common.codeblocks.model

import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

interface ContainerBlock {
    fun BlockEditor.InputSlotScope.composeBody() {}
}