package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.modules.ui2.UiScope

interface DocPage {
    val location: String
    val name: String

    fun UiScope.compose()
}