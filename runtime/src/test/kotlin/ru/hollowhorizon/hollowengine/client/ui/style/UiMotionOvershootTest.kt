package ru.hollowhorizon.hollowengine.client.ui.style

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.flattenModifiers
import ru.hollowhorizon.hollowengine.client.ui.toStylePatch
import ru.hollowhorizon.hollowengine.client.ui.translate
import kotlin.test.assertTrue

class UiMotionOvershootTest {
    @Test
    fun `cubic bezier output and interpolation preserve overshoot`() {
        val easing = TransitionEasing.CubicBezier(0.34f, 1.56f, 0.64f, 1f)
        val progress = easing.transform(0.5f)
        val from = listOf(Modifier.translate(0f, 0f)).flattenModifiers().toStylePatch().resolve()
        val to = listOf(Modifier.translate(100f, 0f)).flattenModifiers().toStylePatch().resolve()

        val interpolated = from.interpolate(to, UiTransitionProgress.all(progress))

        assertTrue(progress > 1f)
        assertTrue(interpolated.translate.x > 100f)
    }

    @Test
    fun `hss transition resolver preserves cubic bezier overshoot`() {
        val stylesheet = compileHss(
            """
                #logo {
                    scale: 0.9;
                    rotate: 0 0 0;
                    transition: all 420ms cubic-bezier(0.34, 1.56, 0.64, 1);
                }
                #logo:hover {
                    scale: 1.2;
                    rotate: 0 0 360;
                }
            """.trimIndent(),
        )
        val node = BoxNode(id = "logo")
        val resolver = UiModifierResolver(stylesheet = stylesheet)
        resolver.resolve(node, nowMillis = 0L)
        node.states += UiState.HOVER
        resolver.resolve(node, nowMillis = 1L)

        resolver.resolve(node, nowMillis = 211L)

        assertTrue(node.resolvedSnapshot.scale.x > 1.2f, "scale was ${node.resolvedSnapshot.scale.x}")
        assertTrue(node.resolvedSnapshot.rotate.z > 360f, "rotation was ${node.resolvedSnapshot.rotate.z}")
    }
}
