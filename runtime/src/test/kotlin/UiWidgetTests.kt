import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.CheckboxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawCheckboxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawSliderCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextFieldChromeCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUiInputController
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.SliderNode
import ru.hollowhorizon.hollowengine.client.ui.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.UiCheckboxVariant
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.UiScrollbarPointerArea
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.UiTextInputFilter
import ru.hollowhorizon.hollowengine.client.ui.UiTextFieldMode
import ru.hollowhorizon.hollowengine.client.ui.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.caretIndexAt
import ru.hollowhorizon.hollowengine.client.ui.caretPosition
import ru.hollowhorizon.hollowengine.client.ui.dragStateAt
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.pointerAreaAt
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.selectionRects
import ru.hollowhorizon.hollowengine.client.ui.scrollWheelDelta
import ru.hollowhorizon.hollowengine.client.ui.trackClickOffset
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiWidgetTests {
    @Test
    fun `xml builds slider checkbox and text field nodes with attributes`() {
        val root = parseUi(
            """
            <box>
                <slider id="volume" min="0" max="10" value="4" step="0.5" />
                <checkbox id="enabled" checked="true" variant="switch" />
                <text-field id="name" value="Hollow" mode="multi-line" filter="integer" multi-caret="true" />
            </box>
            """.trimIndent(),
        )

        val slider = assertIs<SliderNode>(root.children[0])
        val checkbox = assertIs<CheckboxNode>(root.children[1])
        val field = assertIs<TextFieldNode>(root.children[2])

        assertEquals(4f, slider.value)
        assertEquals(0.5f, slider.step)
        assertTrue(checkbox.checked)
        assertEquals(UiCheckboxVariant.SWITCH, checkbox.variant)
        assertEquals(UiTextFieldMode.MULTI_LINE, field.mode)
        assertEquals(UiTextInputFilter.INTEGER, field.filter)
        assertTrue(field.multiCaret)
    }

    @Test
    fun `widget hss styles are emitted into render commands`() {
        val stylesheet = compileHss(
            """
            slider {
                slider-track: #112233;
                slider-active-track: #445566;
                slider-thumb: #FFFFFF;
                slider-track-thickness: 6px;
            }
            checkbox {
                checkbox-variant: radio;
                checkbox-active: #778899;
            }
            text-field {
                caret-color: #FF0000;
                selection-color: rgba(0, 128, 255, 0.4);
                line-numbers: true;
            }
            """.trimIndent(),
        )
        val root = parseUi(
            """
            <box>
                <slider value="0.5" />
                <checkbox checked="true" />
                <text-field value="abc" />
            </box>
            """.trimIndent(),
        )

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 220f, 80f)
        val slider = assertIs<DrawSliderCommand>(frame.commands.first { it is DrawSliderCommand })
        val checkbox = assertIs<DrawCheckboxCommand>(frame.commands.first { it is DrawCheckboxCommand })
        val textField = assertIs<DrawTextFieldChromeCommand>(frame.commands.first { it is DrawTextFieldChromeCommand })

        assertEquals(6f, slider.trackThickness)
        assertIs<UiResolvedPaint.Color>(slider.trackPaint)
        assertEquals(UiCheckboxVariant.RADIO, checkbox.variant)
        assertEquals(1f, textField.caretColor.red)
        assertTrue(textField.showLineNumbers)
    }

    @Test
    fun `slider and text field normalize values`() {
        val slider = SliderNode(value = 0f, min = 0f, max = 10f, step = 2f)
        val changed = slider.setFromLocalX(55f, 100f)
        val field = TextFieldNode(filter = UiTextInputFilter.INTEGER)

        assertTrue(changed)
        assertEquals(6f, slider.value)
        assertTrue(field.insert("12"))
        assertFalse(field.insert("."))
        assertEquals("12", field.value)
    }

    @Test
    fun `text field edits at caret and replaces selection`() {
        val field = TextFieldNode(value = "abc")

        field.moveCaret(1)
        assertTrue(field.insert("X"))
        assertEquals("aXbc", field.value)
        assertEquals(2, field.caret)

        field.setSelection(1, 3)
        assertTrue(field.insert("Y"))
        assertEquals("aYc", field.value)
        assertEquals(2, field.caret)
        assertFalse(field.hasSelection)
    }

    @Test
    fun `text field backspace moves caret by one character`() {
        val field = TextFieldNode(value = "ab c")

        assertTrue(field.backspace())
        assertEquals("ab ", field.value)
        assertEquals(3, field.caret)

        assertTrue(field.backspace())
        assertEquals("ab", field.value)
        assertEquals(2, field.caret)
    }

    @Test
    fun `multiline text field preserves line integrity while editing`() {
        val field = TextFieldNode(value = "a\nb", mode = UiTextFieldMode.MULTI_LINE)

        assertEquals(3, field.caret)
        assertTrue(field.backspace())
        assertEquals("a\n", field.value)
        assertEquals(2, field.caret)
        assertTrue(field.backspace())
        assertEquals("a", field.value)
        assertEquals(1, field.caret)

        assertTrue(field.insert("\nc"))
        assertEquals("a\nc", field.value)
        assertEquals(3, field.caret)
    }

    @Test
    fun `text layout preserves trailing whitespace for caret geometry`() {
        val layout = UiTextLayouter.layout(
            text = "a ",
            width = 200f,
            height = 40f,
            wrap = false,
            align = UiTextAlign.LEFT,
            fontSize = 10f,
            preserveWhitespace = true,
        )

        assertEquals("a ", layout.lines.single().text)
        assertEquals(2, layout.lines.single().sourceLength)
        assertTrue(layout.caretPosition(2, 10f).x > layout.caretPosition(1, 10f).x)
        assertEquals(2, layout.caretIndexAt(layout.caretPosition(2, 10f).x, 5f, 10f))
    }

    @Test
    fun `multiline text layout maps caret indexes across line breaks`() {
        val layout = UiTextLayouter.layout(
            text = "a\nb",
            width = 200f,
            height = 80f,
            wrap = false,
            align = UiTextAlign.LEFT,
            fontSize = 10f,
            preserveWhitespace = true,
        )

        assertEquals(2, layout.lines.size)
        assertEquals(0f, layout.caretPosition(2, 10f).x)
        assertTrue(layout.caretPosition(2, 10f).y > layout.caretPosition(1, 10f).y)
        assertEquals(2, layout.caretIndexAt(0f, layout.caretPosition(2, 10f).y + 1f, 10f))
    }

    @Test
    fun `text layout returns selection rectangles`() {
        val layout = UiTextLayouter.layout("abcd", 200f, 40f, false, UiTextAlign.LEFT, 10f, preserveWhitespace = true)
        val rects = layout.selectionRects(1, 3, 10f)

        assertEquals(1, rects.size)
        assertTrue(rects.single().width > 0f)
    }

    @Test
    fun `text field emits chrome and text commands`() {
        val root = parseUi("""<text-field placeholder="Name" />""")

        val frame = HollowUiRuntime().frame(root, 120f, 40f)

        assertTrue(frame.commands.any { it is DrawTextFieldChromeCommand })
        assertTrue(frame.commands.any { it is DrawTextCommand && it.text == "Name" })
    }

    @Test
    fun `input controller preserves widget state across rebuilt trees`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        var root = parseUi(
            """
            <box>
                <checkbox id="enabled" />
                <slider id="amount" min="0" max="10" value="0" />
            </box>
            """.trimIndent(),
        )
        controller.prepareRoot(root)
        val frame = runtime.frame(root, 200f, 80f)
        val checkbox = assertIs<CheckboxNode>(root.children[0])
        val slider = assertIs<SliderNode>(root.children[1])
        val checkboxRect = frame.layout[checkbox].rect
        val sliderRect = frame.layout[slider].rect

        controller.mouseClicked(
            frame,
            checkboxRect.x + checkboxRect.width / 2f,
            checkboxRect.y + checkboxRect.height / 2f,
            0,
            dispatch = { false },
            openUrl = {},
        )
        controller.mouseClicked(
            frame,
            sliderRect.x + sliderRect.width,
            sliderRect.y + sliderRect.height / 2f,
            0,
            dispatch = { false },
            openUrl = {},
        )

        root = parseUi(
            """
            <box>
                <checkbox id="enabled" />
                <slider id="amount" min="0" max="10" value="0" />
            </box>
            """.trimIndent(),
        )
        controller.prepareRoot(root)

        assertTrue(assertIs<CheckboxNode>(root.children[0]).checked)
        assertEquals(10f, assertIs<SliderNode>(root.children[1]).value)
    }

    @Test
    fun `input controller moves caret to clicked text position`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val root = parseUi("""<text-field id="name" value="abc" />""")
        val field = assertIs<TextFieldNode>(root.children.single())
        controller.prepareRoot(root)
        val frame = runtime.frame(root, 200f, 40f)
        val command = assertIs<DrawTextFieldChromeCommand>(frame.commands.first { it is DrawTextFieldChromeCommand })
        val content = frame.layout[field].content
        val caret = command.layout.caretPosition(1, command.fontSize)

        controller.mouseClicked(frame, content.x + caret.x, content.y + caret.y + 1f, 0, dispatch = { false }, openUrl = {})

        assertEquals(1, field.caret)
    }

    @Test
    fun `input controller clicks text field padding and top half`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val root = parseUi("""<text-field id="name" value="abc" width="100px" />""")
        val field = assertIs<TextFieldNode>(root.children.single())
        controller.prepareRoot(root)
        val frame = runtime.frame(root, 120f, 40f)
        val rect = frame.layout[field].rect

        controller.mouseClicked(frame, rect.x + 1f, rect.y + 1f, 0, dispatch = { false }, openUrl = {})
        assertEquals(0, field.caret)

        controller.mouseClicked(frame, rect.x + rect.width - 1f, rect.y + 1f, 0, dispatch = { false }, openUrl = {})
        assertEquals(field.value.length, field.caret)
    }

    @Test
    fun `input controller selects text by mouse drag`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val root = parseUi("""<text-field id="name" value="abcd" />""")
        val field = assertIs<TextFieldNode>(root.children.single())
        controller.prepareRoot(root)
        val frame = runtime.frame(root, 220f, 40f)
        val command = assertIs<DrawTextFieldChromeCommand>(frame.commands.first { it is DrawTextFieldChromeCommand })
        val content = frame.layout[field].content
        val start = command.layout.caretPosition(1, command.fontSize)
        val end = command.layout.caretPosition(3, command.fontSize)

        controller.mouseClicked(frame, content.x + start.x, content.y + start.y + 1f, 0, dispatch = { false }, openUrl = {})
        controller.mouseDragged(frame, content.x + end.x, content.y + end.y + 1f, 0, 0f, 0f, dispatch = { false })

        assertEquals(1, field.selectionStart)
        assertEquals(3, field.selectionEnd)
    }

    @Test
    fun `input controller clears old text selection when another field gains focus`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val first = TextFieldNode(value = "abcd")
        val second = TextFieldNode(value = "wxyz")
        val root = BoxNode(modifiers = listOf(Modifier.layout(LayoutType.ROW)))
        root.children += first
        root.children += second

        controller.prepareRoot(root)
        val frame = runtime.frame(root, 260f, 40f)
        val firstCommand = frame.commands.filterIsInstance<DrawTextFieldChromeCommand>().first { it.node === first }
        val firstContent = frame.layout[first].content
        val start = firstCommand.layout.caretPosition(1, firstCommand.fontSize)
        val end = firstCommand.layout.caretPosition(3, firstCommand.fontSize)
        controller.mouseClicked(frame, firstContent.x + start.x, firstContent.y + start.y + 1f, 0, dispatch = { false }, openUrl = {})
        controller.mouseDragged(frame, firstContent.x + end.x, firstContent.y + end.y + 1f, 0, 0f, 0f, dispatch = { false })
        assertTrue(first.hasSelection)

        val secondRect = frame.layout[second].rect
        controller.mouseClicked(frame, secondRect.x + 1f, secondRect.y + 1f, 0, dispatch = { false }, openUrl = {})

        assertFalse(first.hasSelection)
    }

    @Test
    fun `text field intrinsic width follows content instead of fixed default`() {
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(value = "Hi")
        val root = BoxNode(modifiers = listOf(Modifier.layout(LayoutType.ROW)))
        root.children += field

        val frame = runtime.frame(root, 300f, 40f)
        val width = frame.layout[field].rect.width

        assertTrue(width < 80f)
        assertTrue(width >= frame.layout[field].content.width)
    }

    @Test
    fun `input controller uses scrolled transforms for text field caret clicks`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(value = "abc")
        val root = BoxNode(
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(120.px, 30.px),
                    Modifier.input(scrollable = true),
                    Modifier.clip(),
                )
            )
        )
        root.children += BoxNode(
            modifiers = listOf(Modifier.size(120.px, 40.px))
        )
        root.children += field

        controller.prepareRoot(root)
        runtime.frame(root, 120f, 30f)
        runtime.setScrollImmediate(root, y = 36f)
        controller.prepareRoot(root)
        val frame = runtime.frame(root, 120f, 30f)
        val command = assertIs<DrawTextFieldChromeCommand>(frame.commands.first { it is DrawTextFieldChromeCommand })
        val content = frame.layout[field].content
        val caret = command.layout.caretPosition(2, command.fontSize)

        controller.mouseClicked(frame, content.x + caret.x - 0.1f, content.y + caret.y + 1f, 0, dispatch = { false }, openUrl = {})

        assertEquals(2, field.caret)
    }

    @Test
    fun `scrollable text field without overflow does not reserve scrollbar input gutter`() {
        val controller = HollowUiInputController()
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(
            value = "abc",
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(120.px, 20.px),
                    Modifier.input(scrollable = true),
                )
            ),
        )
        controller.prepareRoot(field)
        val frame = runtime.frame(field, 140f, 40f)
        val layout = frame.layout[field]

        assertFalse(frame.commands.any { it is DrawScrollbarCommand })
        controller.mouseClicked(frame, layout.rect.x + layout.rect.width - 1f, layout.rect.y + 1f, 0, dispatch = { false }, openUrl = {})

        assertEquals(field.value.length, field.caret)
    }

    @Test
    fun `auto width multiline text field does not wrap while it can fit content`() {
        val runtime = HollowUiRuntime()
        val root = BoxNode(modifiers = listOf(Modifier.layout(LayoutType.ROW)))
        val field = TextFieldNode(value = "fff", mode = UiTextFieldMode.MULTI_LINE)
        root.children += field

        val frame = runtime.frame(root, 200f, 40f)
        val chrome = frame.commands.filterIsInstance<DrawTextFieldChromeCommand>().single()
        val text = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node === field }

        assertEquals(1, chrome.layout.lines.size)
        assertEquals("fff", text.text)
        assertTrue(frame.layout[field].content.width >= chrome.layout.lines.single().naturalWidth)
    }

    @Test
    fun `scrollable text field exposes horizontal range for nowrap text`() {
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(
            value = "abcdefghijklmnopqrstuvwxyz",
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(40.px, 20.px),
                    Modifier.input(scrollable = true),
                    Modifier.textWrap(false),
                )
            ),
        )

        val frame = runtime.frame(field, 80f, 40f)
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.HORIZONTAL }

        assertTrue(frame.layout[field].scrollRange.x > 0f)
        assertEquals(field, scrollbar.node)
    }

    @Test
    fun `scrollbar thumb starts drag while track click computes jump offset`() {
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(
            value = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz",
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(120.px, 20.px),
                    Modifier.input(scrollable = true),
                    Modifier.textWrap(false),
                )
            ),
        )
        val frame = runtime.frame(field, 160f, 60f)
        val layout = frame.layout[field]
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.HORIZONTAL }
        val thumbX = scrollbar.thumb.x + scrollbar.thumb.width * 0.5f
        val centerY = scrollbar.thumb.y + scrollbar.thumb.height * 0.5f
        val trackX = (scrollbar.thumb.x + scrollbar.thumb.width + 2f)
            .coerceAtMost(scrollbar.track.x + scrollbar.track.width - 1f)

        assertEquals(UiScrollbarPointerArea.THUMB, scrollbar.pointerAreaAt(thumbX, centerY))
        assertTrue(scrollbar.dragStateAt(thumbX, centerY)!!.offsetFor(layout, scrollbar.track.x + scrollbar.track.width, centerY).x > 0f)
        assertEquals(UiScrollbarPointerArea.TRACK, scrollbar.pointerAreaAt(trackX, centerY))
        assertEquals(null, scrollbar.dragStateAt(trackX, centerY))
        assertTrue(scrollbar.trackClickOffset(layout, trackX, centerY).x > 0f)
    }

    @Test
    fun `horizontal scrollbar hit testing follows visual transform`() {
        val runtime = HollowUiRuntime()
        val root = BoxNode(
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(60.px, 24.px),
                    Modifier.input(scrollable = true),
                    Modifier.pivot(0.px, 0.px),
                    Modifier.scale(2f),
                )
            )
        )
        root.children += BoxNode(modifiers = listOf(Modifier.size(180.px, 24.px)))

        val frame = runtime.frame(root, 200f, 80f)
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.HORIZONTAL }
        val localX = scrollbar.thumb.x + scrollbar.thumb.width * 0.5f
        val localY = scrollbar.thumb.y + scrollbar.thumb.height * 0.5f
        val visual = scrollbar.transform.transform(localX, localY, 0f)

        assertEquals(null, scrollbar.pointerAreaAt(visual.x, localY))
        assertEquals(null, frame.scrollbarAt(visual.x, localY))
        assertEquals(UiScrollbarPointerArea.THUMB, scrollbar.pointerAreaAt(visual.x, visual.y))
        assertEquals(scrollbar, frame.scrollbarAt(visual.x, visual.y))
        assertTrue(scrollbar.dragStateAt(visual.x, visual.y)!!.offsetFor(frame.layout[root], visual.x + 40f, visual.y).x > 0f)
    }

    @Test
    fun `vertical scrollbar hit testing follows visual transform`() {
        val runtime = HollowUiRuntime()
        val root = BoxNode(
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(40.px, 32.px),
                    Modifier.layout(LayoutType.COLUMN),
                    Modifier.input(scrollable = true),
                    Modifier.pivot(0.px, 0.px),
                    Modifier.scale(2f),
                )
            )
        )
        root.children += BoxNode(modifiers = listOf(Modifier.size(40.px, 96.px)))

        val frame = runtime.frame(root, 160f, 120f)
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.VERTICAL }
        val localX = scrollbar.thumb.x + scrollbar.thumb.width * 0.5f
        val localY = scrollbar.thumb.y + scrollbar.thumb.height * 0.5f
        val visual = scrollbar.transform.transform(localX, localY, 0f)

        assertEquals(null, scrollbar.pointerAreaAt(localX, visual.y))
        assertEquals(null, frame.scrollbarAt(localX, visual.y))
        assertEquals(UiScrollbarPointerArea.THUMB, scrollbar.pointerAreaAt(visual.x, visual.y))
        assertEquals(scrollbar, frame.scrollbarAt(visual.x, visual.y))
        assertTrue(scrollbar.dragStateAt(visual.x, visual.y)!!.offsetFor(frame.layout[root], visual.x, visual.y + 40f).y > 0f)
    }

    @Test
    fun `shift wheel maps vertical wheel delta to horizontal scroll`() {
        val regular = scrollWheelDelta(UiScrollOffset(x = 100f, y = 100f), scrollX = 0.0, scrollY = 1.0, horizontalModifier = false)
        val shifted = scrollWheelDelta(UiScrollOffset(x = 100f, y = 100f), scrollX = 0.0, scrollY = 1.0, horizontalModifier = true)
        val horizontalOnly = scrollWheelDelta(UiScrollOffset(x = 100f, y = 0f), scrollX = 0.0, scrollY = 1.0, horizontalModifier = false)

        assertEquals(0f, regular.x, 0.0001f)
        assertEquals(-1f, regular.y, 0.0001f)
        assertEquals(-1f, shifted.x, 0.0001f)
        assertEquals(0f, shifted.y, 0.0001f)
        assertEquals(-1f, horizontalOnly.x, 0.0001f)
        assertEquals(0f, horizontalOnly.y, 0.0001f)
    }

    @Test
    fun `scrollable text field reserves gutter for vertical scrollbar`() {
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(
            value = "a\nb\nc\nd",
            mode = UiTextFieldMode.MULTI_LINE,
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(80.px, 20.px),
                    Modifier.input(scrollable = true),
                )
            ),
        )

        val frame = runtime.frame(field, 120f, 60f)
        val layout = frame.layout[field]
        val text = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node === field }
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.VERTICAL }

        assertTrue(layout.scrollRange.y > 0f)
        assertTrue(layout.content.width < layout.scrollArea.width)
        assertEquals(layout.content.width, text.rect.width)
        val trackLeft = scrollbar.transform.transform(scrollbar.track.x, scrollbar.track.y, 0f).x
        assertTrue(trackLeft >= layout.content.x + layout.content.width)
    }

    @Test
    fun `scrollable box allows nowrap text field to overflow horizontally`() {
        val runtime = HollowUiRuntime()
        val root = BoxNode(
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(40.px, 24.px),
                    Modifier.input(scrollable = true),
                    Modifier.clip(),
                )
            )
        )
        val field = TextFieldNode(value = "abcdefghijklmnopqrstuvwxyz", modifiers = listOf(Modifier.textWrap(false)))
        root.children += field

        val frame = runtime.frame(root, 80f, 40f)
        val rootLayout = frame.layout[root]
        val fieldLayout = frame.layout[field]
        val scrollbar = frame.commands
            .filterIsInstance<DrawScrollbarCommand>()
            .single { it.orientation == ScrollbarOrientation.HORIZONTAL }

        assertTrue(fieldLayout.rect.width > rootLayout.content.width)
        assertTrue(rootLayout.scrollRange.x > 0f)
        assertEquals(root, scrollbar.node)
    }

    @Test
    fun `animated scroll requests continuous frame refresh`() {
        val runtime = HollowUiRuntime()
        val field = TextFieldNode(
            value = "abcdefghijklmnopqrstuvwxyz",
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(40.px, 20.px),
                    Modifier.input(scrollable = true),
                    Modifier.textWrap(false),
                )
            ),
        )
        val initial = runtime.frame(field, 80f, 40f)
        runtime.scroll(field, 16f, 0f)

        val scrolled = runtime.frame(field, 80f, 40f, nowMillis = System.currentTimeMillis())

        assertTrue(initial.layout[field].scrollRange.x > 0f)
        assertTrue(scrolled.requiresContinuousRefresh())
    }

    @Test
    fun `scroll target hit testing honors transforms`() {
        val runtime = HollowUiRuntime()
        val root = BoxNode(
            modifiers = listOf(
                Modifier.then(
                    Modifier.size(40.px, 24.px),
                    Modifier.input(scrollable = true),
                    Modifier.scale(2f),
                )
            )
        )
        root.children += BoxNode(modifiers = listOf(Modifier.size(80.px, 24.px)))

        val frame = runtime.frame(root, 120f, 80f)

        assertEquals(root, frame.scrollTargetAt(55f, 10f))
    }
}
