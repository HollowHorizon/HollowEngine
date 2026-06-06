package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.HollowUiComposition
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

abstract class HollowComposeUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : HollowUiScreen(title, stylesheet) {
    private val composition = HollowUiComposition()

    @Composable
    protected abstract fun Content()

    override fun buildUi(): UiNode {
        return composition.setContent { Content() }
    }

    override fun rebuildEveryFrame(): Boolean {
        return composition.applyPendingChanges()
    }

    override fun removed() {
        composition.close()
        super.removed()
    }
}
