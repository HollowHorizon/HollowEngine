package ru.hollowhorizon.hollowengine.client.ui

import kotlinx.coroutines.Dispatchers
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import kotlin.coroutines.CoroutineContext

class HollowUiSurface(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    scrollState: UiScrollState = UiScrollState(),
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
) : AutoCloseable {
    private val composition = HollowUiComposition(coroutineContext)
    private val runtime = HollowUiRuntime(theme, stylesheet, scrollState)
    private var hasContent = false

    val root: BoxNode
        get() {
            check(hasContent) { "UI content has not been set" }
            return composition.root
        }

    fun setContent(content: HollowUiContent): BoxNode {
        hasContent = true
        return composition.setContent(content)
    }

    fun composeRoot(nowNanos: Long = System.nanoTime()): BoxNode {
        check(hasContent) { "UI content has not been set" }
        return composition.frameRoot(nowNanos)
    }

    fun applyPendingChanges(nowNanos: Long = System.nanoTime()): Boolean {
        if (!hasContent) return false
        return composition.applyPendingChanges(nowNanos)
    }

    fun frame(
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
        prepareRoot: (BoxNode) -> Unit = {},
    ): HollowUiFrame {
        check(hasContent) { "UI content has not been set" }
        val root = composition.frameRoot(nowMillis * NanosPerMillisecond)
        prepareRoot(root)
        return runtime.frame(root, width, height, bindings, nowMillis)
    }

    fun frame(
        content: HollowUiContent,
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
        prepareRoot: (BoxNode) -> Unit = {},
    ): HollowUiFrame {
        setContent(content)
        return frame(width, height, bindings, nowMillis, prepareRoot)
    }

    fun frame(
        root: UiNode,
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
        prepareRoot: (UiNode) -> Unit = {},
    ): HollowUiFrame {
        prepareRoot(root)
        return runtime.frame(root, width, height, bindings, nowMillis)
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset {
        return runtime.scroll(node, deltaX, deltaY)
    }

    fun setScrollImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        return runtime.setScrollImmediate(node, x, y)
    }

    override fun close() {
        composition.close()
    }

    private companion object {
        const val NanosPerMillisecond = 1_000_000L
    }
}
