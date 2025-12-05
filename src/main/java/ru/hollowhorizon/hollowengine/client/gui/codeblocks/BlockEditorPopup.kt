package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.UiNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockProvider

context(editor: BlockEditor)
fun buildMenuFromProvider(provider: BlockProvider, rootUiNode: UiNode): SubMenuItem<Vec2f> {
    return SubMenuItem(provider.name, null) {
        fillCategory(provider.rootCategory, rootUiNode)
    }
}

context(editor: BlockEditor)
private fun SubMenuItem<Vec2f>.fillCategory(category: BlockCategory, rootUiNode: UiNode) {
    category.blocks.forEach { entry ->
        item(entry.name) { screenPos ->
            val newBlock = entry.factory()
            val localPos = rootUiNode.toLocal(screenPos)
            newBlock.setPosition(localPos.x, localPos.y)
            editor.rootBlocks.add(newBlock)
        }
    }

    if (category.blocks.isNotEmpty() && category.subCategories.isNotEmpty()) {
        divider()
    }

    category.subCategories.forEach { subCat ->
        subMenu(subCat.name) {
            fillCategory(subCat, rootUiNode)
        }
    }
}
