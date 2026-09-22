package ru.hollowhorizon.hollowengine.client.ui.style

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.UiMeasurePolicies
import kotlin.test.assertEquals

/**
 * `:closing` is the one state no content sets: a host playing its exit animation hands it to the
 * runtime, which has to put the *whole* tree in it, or a rule anchored on the root (the usual shape,
 * `#root:closing #child`) would never match the child it styles.
 */
class ClosingStateTest {
    private val sheet = compileHss(
        """
        #dim { opacity: 1; }
        #root:closing #dim { opacity: 0; }
        """.trimIndent(),
    )

    private fun tree(): Pair<BoxNode, BoxNode> {
        val dim = BoxNode(id = "dim")
        val root = BoxNode(id = "root", measurePolicy = UiMeasurePolicies.Column).also { it.children.add(dim) }
        dim.layoutState.attachTo(root)
        return root to dim
    }

    @Test
    fun `closing the host restyles the whole tree`() {
        val (root, dim) = tree()
        val runtime = HollowUiRuntime(stylesheet = sheet)

        runtime.frame(root, 200f, 200f, -1f, -1f, 0L)
        assertEquals(1f, dim.resolvedSnapshot.opacity, "the exit rule is idle while the host is up")

        runtime.isClosing = true
        runtime.frame(root, 200f, 200f, -1f, -1f, 0L)
        assertEquals(0f, dim.resolvedSnapshot.opacity, "a rule anchored on the root sees the state on the root")

        runtime.isClosing = false
        runtime.frame(root, 200f, 200f, -1f, -1f, 0L)
        assertEquals(1f, dim.resolvedSnapshot.opacity, "a host that is not closing keeps nothing of the state")
    }
}
