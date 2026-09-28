package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A node's id is its identity: a composable that is recomposed with another id must end up as a node
 * with that id. A reused node kept its first id, so a dock panel that switched windows still answered
 * clicks as the first window.
 */
class NodeIdentityTest {
    @Test
    fun `a node recomposed with another id carries the new id`() {
        var window by mutableStateOf("timeline")
        HollowUiSurface().use { surface ->
            surface.setContent {
                Box(id = "panel-$window-content", modifier = Modifier.size(100.px, 60.px).input(clickable = true))
            }
            surface.frame(200f, 120f, 10f, 10f, 0L)

            window = "assets"
            Snapshot.sendApplyNotifications()
            val frame = surface.frame(200f, 120f, 10f, 10f, 1L)

            assertEquals("panel-assets-content", frame.hitTest(10f, 10f)?.node?.id)
        }
    }
}
