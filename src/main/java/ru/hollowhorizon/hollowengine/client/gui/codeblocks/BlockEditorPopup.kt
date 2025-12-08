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
        item(entry.name, entry.icon) { screenPos ->
            val newBlock = entry.factory()
            val localPos = rootUiNode.toLocal(screenPos)
            newBlock.setPosition(localPos.x, localPos.y)
            editor.rootBlocks.add(newBlock)
        }
    }

    if (category.blocks.isNotEmpty() && category.subCategories.isNotEmpty()) {
        divider()
    }

    val lastIndex = category.subCategories.lastIndex
    category.subCategories.forEachIndexed { i, subCat ->
        subMenu(subCat.name, subCat.icon, subCat.color) {
            fillCategory(subCat, rootUiNode)
        }
        if (i != lastIndex) divider()
    }
}
