import ru.hollowhorizon.hollowengine.client.gui.scripting.completionScrollIndex
import ru.hollowhorizon.hollowengine.client.gui.scripting.completionSelectionScrollIndex
import ru.hollowhorizon.hollowengine.client.gui.scripting.completionWindowStart
import ru.hollowhorizon.hollowengine.client.ui.UiTextFieldCompletionPopupGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HollowIdeCompletionPopupTests {
    @Test
    fun `manual scrolling advances completion window independently of selection`() {
        val start = completionWindowStart(
            totalCount = 500,
            selectedIndex = 0,
            selectedChanged = false,
            scrollOffset = 120 * 20f,
            rowHeight = 20f,
            visibleRows = 10,
        )

        assertEquals(108, start)
    }

    @Test
    fun `keyboard selection remains visible without changing manual scroll calculation`() {
        val scrollIndex = completionScrollIndex(
            totalCount = 500,
            scrollOffset = 120 * 20f,
            rowHeight = 20f,
            visibleRows = 10,
        )

        assertEquals(120, scrollIndex)
        assertEquals(191, completionSelectionScrollIndex(500, 200, scrollIndex, 10))
        assertEquals(80, completionSelectionScrollIndex(500, 80, scrollIndex, 10))
    }

    @Test
    fun `completion hint space is not counted as a selectable row`() {
        val geometry = UiTextFieldCompletionPopupGeometry(
            x = 0f,
            y = 0f,
            width = 100f,
            height = 45f,
            listHeight = 24f,
            rowHeight = 20f,
            itemCount = 1,
            visibleRows = 1,
        )

        assertEquals(1, geometry.visibleRows)
        assertEquals(0, geometry.rowAt(8f, 8f))
        assertNull(geometry.rowAt(8f, 32f))
    }
}
