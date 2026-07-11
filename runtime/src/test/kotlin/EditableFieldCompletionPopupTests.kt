import ru.hollowhorizon.hollowengine.client.ui.widgets.completionScrollRowIndex
import ru.hollowhorizon.hollowengine.client.ui.widgets.completionSelectionRowIndex
import ru.hollowhorizon.hollowengine.client.ui.widgets.completionWindowStartIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class EditableFieldCompletionPopupTests {
    @Test
    fun `manual scrolling advances completion window independently of selection`() {
        val start = completionWindowStartIndex(
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
        val scrollIndex = completionScrollRowIndex(
            totalCount = 500,
            scrollOffset = 120 * 20f,
            rowHeight = 20f,
            visibleRows = 10,
        )

        assertEquals(120, scrollIndex)
        assertEquals(191, completionSelectionRowIndex(500, 200, scrollIndex, 10))
        assertEquals(80, completionSelectionRowIndex(500, 80, scrollIndex, 10))
    }
}
