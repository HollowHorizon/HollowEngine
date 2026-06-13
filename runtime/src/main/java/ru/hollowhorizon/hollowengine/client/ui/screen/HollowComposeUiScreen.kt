package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.client.ui.LocalUiFrameTimeNanos
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

abstract class HollowComposeUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : HollowUiScreen(title, stylesheet) {
    private var contentInstalled = false
    private var frameTimeNanos by mutableStateOf(0L)

    @Composable
    protected abstract fun Content()

    override fun buildUi(): UiNode {
        if (!contentInstalled) {
            contentInstalled = true
            return installComposeContent {
                CompositionLocalProvider(LocalUiFrameTimeNanos provides frameTimeNanos) {
                    Content()
                }
            }
        }
        return composeRoot()
    }

    override fun applyPendingUiChanges(nowNanos: Long): Boolean {
        if (rebuildEveryFrame()) frameTimeNanos = nowNanos
        return applyComposePendingChanges(nowNanos)
    }
}
