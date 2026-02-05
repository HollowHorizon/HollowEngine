package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

class GLTFFile(path: String) : ModelEditorFile(path) {
    init {
        if (path.startsWith("assets")) {
            modelController.model.set(path.substringAfter('/').replaceFirst('/', ':'))
        }
    }

    override fun save() {
        // GLTF files are read-only in this editor
    }

    override val hasSidebar = false
}
