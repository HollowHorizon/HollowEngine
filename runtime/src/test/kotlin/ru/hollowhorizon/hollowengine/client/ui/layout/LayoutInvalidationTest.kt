package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkKeyframes
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkPeriodMillis
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class LayoutInvalidationTest {
    @Test
    fun `draw-only animation reuses the layout, a size change rebuilds it`() {
        val box = BoxNode(
            id = "b",
            modifiers = listOf(
                Modifier.size(40.px, 40.px)
                    .animation(UiCaretBlinkKeyframes, UiCaretBlinkPeriodMillis, iterationCount = Float.POSITIVE_INFINITY),
            ),
        )
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(box) }
        val runtime = HollowUiRuntime()

        val f1 = runtime.frame(root, 200f, 200f, -1f, -1f, 0L)
        val f2 = runtime.frame(root, 200f, 200f, -1f, -1f, 400L)
        assertSame(f1.layout, f2.layout, "opacity animation advancing does not rebuild the layout")

        val f3 = runtime.frame(root, 300f, 200f, -1f, -1f, 800L)
        assertNotSame(f2.layout, f3.layout, "a viewport width change rebuilds the layout")
    }
}
