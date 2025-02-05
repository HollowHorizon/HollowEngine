package ru.hollowhorizon.hollowengine.docs.pages.story.npcs

import de.fabmax.kool.modules.ui2.*

object NpcsPage: Composable {
    override fun UiScope.compose() {
        Text("Персонажи (или же НИПы/НПС) - сущности, которыми можно управлять через скрипты. Также им можно настраивать имена, модели, действия, товары для торговли, создавать квесты и многое другое!") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
    }
}