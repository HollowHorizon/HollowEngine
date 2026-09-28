package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Nodes ease a change of their transform by default. A layer that follows the pointer, such as the
 * world of a graph canvas, opts out, and its children must then be where it moved them on the very
 * next frame, both drawn and hit there.
 */
class TransformedLayerTest {
    @Test
    fun `children of a layer without transitions follow its move and scale on the next frame`() {
        var pan by mutableStateOf(0f)
        var zoom by mutableStateOf(1f)
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(mode = UiBoxMode.STACK, modifier = Modifier.size(400.px, 400.px)) {
                    Box(
                        mode = UiBoxMode.STACK,
                        modifier = Modifier.position(0.px, 0.px).size(100.percent, 100.percent)
                            .pivot(0.px, 0.px).translate(pan, pan).scale(zoom).transition(),
                    ) {
                        Box(id = "node", modifier = Modifier.position(10.px, 10.px).size(20.px, 20.px).input(clickable = true))
                    }
                }
            }
            surface.frame(400f, 400f, 0f, 0f, 0L)

            pan = 100f
            zoom = 2f
            Snapshot.sendApplyNotifications()
            val frame = surface.frame(400f, 400f, 0f, 0f, 16L)

            // From 100 + 2 * 10 = 120 to 100 + 2 * 30 = 160.
            assertEquals("node", frame.hitTest(150f, 150f)?.node?.id)
        }
    }
}
