package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.UiModifier
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.background

fun UiModifier.backgrounds(vararg backgrounds: UiRenderer<UiNode>) =
    if (backgrounds.isEmpty()) background(null)
    else background(object : UiRenderer<UiNode> {
        override fun renderUi(node: UiNode) {
            backgrounds.forEach { it.renderUi(node) }
        }
    })
