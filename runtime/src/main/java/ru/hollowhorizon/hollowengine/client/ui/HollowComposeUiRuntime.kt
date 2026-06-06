package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

class HollowComposeUiRuntime(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    scrollState: UiScrollState = UiScrollState(),
) : AutoCloseable {
    private val composition = HollowUiComposition()
    private val runtime = HollowUiRuntime(theme, stylesheet, scrollState)
    private var hasContent = false

    val root: BoxNode
        get() {
            check(hasContent) { "Compose UI content has not been set" }
            return composition.root
        }

    fun setContent(content: HollowUiContent): BoxNode {
        hasContent = true
        return composition.setContent(content)
    }

    fun frame(
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
    ): HollowUiFrame {
        check(hasContent) { "Compose UI content has not been set" }
        val root = composition.frameRoot(nowMillis * NanosPerMillisecond)
        return runtime.frame(root, width, height, bindings, nowMillis)
    }

    fun frame(
        content: HollowUiContent,
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
    ): HollowUiFrame {
        setContent(content)
        return frame(width, height, bindings, nowMillis)
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
