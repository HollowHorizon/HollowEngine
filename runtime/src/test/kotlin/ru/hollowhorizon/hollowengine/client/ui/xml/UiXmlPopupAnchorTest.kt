package ru.hollowhorizon.hollowengine.client.ui.xml

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.HollowUiSurface
import ru.hollowhorizon.hollowengine.client.ui.PopupNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import kotlin.test.assertEquals

class UiXmlPopupAnchorTest {
    @Test
    fun `popup anchor attribute uses target node bounds instead of pointer position`() {
        val surface = HollowUiSurface()
        try {
            surface.setContent {
                UiXmlContent(
                    parseUiXml(
                        """
                        <box id="root" mode="stack" size="200px 200px">
                            <box id="anchor" position="40px 30px" size="20px 10px" />
                            <popup id="popup" anchor="anchor" placement="below-start">
                                <box id="popup-body" size="50px 24px" />
                            </popup>
                        </box>
                        """.trimIndent()
                    )
                )
            }

            surface.frame(200f, 200f, 180f, 180f, 0L)
            val frame = surface.frame(200f, 200f, 180f, 180f, 1_000_000L)
            val popup = frame.layout.nodes.keys.filterIsInstance<PopupNode>().single { it.id == "popup" }

            assertEquals(UiRect(40f, 40f, 50f, 24f), frame.layout[popup].rect)
        } finally {
            surface.close()
        }
    }
}
