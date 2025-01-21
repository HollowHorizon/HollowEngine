package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color

class SimpleTextPage(override val name: String, override val location: String, val text: String): DocPage {
    override fun UiScope.compose() {
        Text(text) {
            modifier.textColor(Color.RED)
        }
    }
}