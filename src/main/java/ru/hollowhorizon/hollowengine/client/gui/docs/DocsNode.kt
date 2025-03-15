package ru.hollowhorizon.hollowengine.client.gui.docs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.utils.lang

class DocsNode(name: String, path: String, val page: Composable? = null) : FileNode(name, path) {
    constructor(path: String, page: Composable? = null) : this(
        "hollowengine.gui.docs.${path.replace('/', '.')}".lang,
        path,
        page
    )

    override fun toggleExpanded() {
        if (!isFolder) return

        // Открываем / Закрываем папку
        isExpanded.set(!isExpanded.value)
    }
}