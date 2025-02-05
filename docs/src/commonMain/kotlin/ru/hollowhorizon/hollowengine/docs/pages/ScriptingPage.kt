package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.modules.ui2.*

object ScriptingPage : Composable {
    override fun UiScope.compose() {
        Text("В данном разделе Вы можете узнать, какой функционал предоставляет скриптинг в HollowEngine.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Text("Разумеется здесь будет не всё, поскольку движок позволяет напрямую обращаться к классам игры и модов. Описать всё - чисто физически невозможно. Здесь Вы сможете узнать лишь о части функционала добавленного движком, или популярных фичах из ванилы или модов.") {
            modifier.isWrapText(true).width(Grow.Std).margin(sizes.gap)
        }
    }
}