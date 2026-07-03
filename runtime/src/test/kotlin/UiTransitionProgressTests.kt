import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.style.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiTransitionProgressTests {
    @Test
    fun `overshooting easing does not complete transition before its duration`() {
        val easing = TransitionEasing.CubicBezier(0.34f, 1.56f, 0.64f, 1f)
        val transition = UiTransition("scale", 1_000L, easing)
        val overshoot = transition.progress(500L)

        assertTrue(overshoot > 1f)
        assertFalse(TransitionProgress.all(overshoot).complete())
        assertFalse(transition.complete(500L))
        assertTrue(transition.complete(1_000L))
    }
}