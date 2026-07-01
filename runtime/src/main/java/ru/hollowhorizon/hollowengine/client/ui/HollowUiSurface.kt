package ru.hollowhorizon.hollowengine.client.ui

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

    fun setContent(content: HollowUiContent): BoxNode {
        hasContent = true
        return composition.setContent(content)
    }

    fun frame(
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        nowNanos: Long = 0L,
    ): HollowUiFrame {
        check(hasContent) { "UI content has not been set" }
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
