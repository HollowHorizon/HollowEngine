package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import kotlin.coroutines.CoroutineContext

class HollowUiSurface(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    scrollState: UiScrollState = UiScrollState(),
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
) : AutoCloseable {
    private val composition = HollowUiComposition(coroutineContext)
    val runtime = HollowUiRuntime(theme, stylesheet, scrollState)
    private var hasContent = false

    private var frameTimeNanos by mutableStateOf(0L)
    private var pointer by mutableStateOf(UiPointer.Unknown)
    private val overlayManager = OverlayManager()

    fun setContent(content: HollowUiContent): BoxNode {
        hasContent = true
        return composition.setContent {
            CompositionLocalProvider(
                LocalUiFrameTimeNanos provides frameTimeNanos,
                LocalPointer provides pointer,
                LocalOverlayManager provides overlayManager,
            ) {
                content()
                OverlayHost()
            }
        }
    }

    /**
     * Opt-in per-frame time. A composable that animates off [LocalUiFrameTimeNanos] recomposes only
     * when this advances, so callers tick it (e.g. a `rebuildEveryFrame` screen) rather than the
     * surface driving every window/HUD/IDE every frame.
     */
    fun advanceFrameTime(nowNanos: Long) {
        frameTimeNanos = nowNanos
    }

    fun frame(
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        nowNanos: Long = 0L,
    ): HollowUiFrame {
        check(hasContent) { "UI content has not been set" }
        pointer = UiPointer(x, y)
        val root = composition.frameRoot(nowNanos)
        return runtime.frame(root, width, height, x, y, nowNanos / NanosPerMillisecond)
    }

    override fun close() {
        composition.close()
    }

    private companion object {
        const val NanosPerMillisecond = 1_000_000L
    }
}
