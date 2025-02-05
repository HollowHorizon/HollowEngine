package ru.hollowhorizon.hollowengine.docs.pages.story

import de.fabmax.kool.modules.ui2.*

object StoryEventsPage : Composable {
    override fun UiScope.compose() {
        Text("В этом разделе рассказано обо всём, что связано с сюжетом: персонажи, сцены, диалоги, игроки, эффекты и т.п.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
    }
}