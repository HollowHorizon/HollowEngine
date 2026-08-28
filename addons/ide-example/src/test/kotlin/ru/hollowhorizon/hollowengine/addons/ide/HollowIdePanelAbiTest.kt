package ru.hollowhorizon.hollowengine.addons.ide

import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdePanel
import kotlin.test.Test
import kotlin.test.assertEquals

class HollowIdePanelAbiTest {
    @Test
    fun `addon and runtime use the same compose panel ABI`() {
        val panel = HollowIdePanel(
            id = "abi-test",
            title = "ABI test",
            content = {},
        )

        assertEquals("abi-test", panel.id)
    }
}
