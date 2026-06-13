import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawShadowCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.ImageNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiCommandRenderer
import ru.hollowhorizon.hollowengine.client.ui.UiLayout
import ru.hollowhorizon.hollowengine.client.ui.UiLayoutEngine
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiInlineItem
import ru.hollowhorizon.hollowengine.client.ui.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.UiRichText
import ru.hollowhorizon.hollowengine.client.ui.UiShadow
import ru.hollowhorizon.hollowengine.client.ui.UiStyleResolver
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.UiTextContent
import ru.hollowhorizon.hollowengine.client.ui.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.UiTextRun
import ru.hollowhorizon.hollowengine.client.ui.UiTextSegment
import ru.hollowhorizon.hollowengine.client.ui.UiTyping
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.bound
import ru.hollowhorizon.hollowengine.client.ui.effects.Shadow
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.selectionRects
import ru.hollowhorizon.hollowengine.client.ui.visibleLineItems
import ru.hollowhorizon.hollowengine.client.ui.withColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiTextLayoutTests {
    @Test
    fun `wrapped rich text lines stay left aligned after styled prefix`() {
        val layout = UiTextLayouter.layout(
            richText = dialogueText(),
            width = DialogueTextWidth,
            height = Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )

        assertTrue(layout.lines.size >= 2)
        layout.lines.forEach { line ->
            assertEquals(0f, line.x)
            assertEquals(0f, line.fragments.first().x)
        }
    }

    @Test
    fun `typing prefix preserves wrapped line origins`() {
        val fullLayout = UiTextLayouter.layout(
            richText = dialogueText(),
            width = DialogueTextWidth,
            height = Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )

        val secondLineEnd = fullLayout.lines.take(2).sumOf { it.sourceLength }
        val visibleLayout = UiTextLayouter.visibleTextPrefix(fullLayout, secondLineEnd, 9f)

        assertTrue(visibleLayout.lines.size >= 2)
        visibleLayout.lines.forEach { line ->
            assertEquals(0f, line.x)
            assertEquals(0f, line.fragments.first().x)
        }
    }

    @Test
    fun `typing partial line keeps first visible word at line origin`() {
        val fullLayout = UiTextLayouter.layout(
            richText = dialogueText(),
            width = DialogueTextWidth,
            height = Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )
        val firstLineLength = fullLayout.lines.first().sourceLength
        val secondLineFirstWord = fullLayout.lines[1].fragments
            .filterIsInstance<UiTextRun>()
            .first()
            .text

        val visibleLayout = UiTextLayouter.visibleTextPrefix(
            fullLayout,
            firstLineLength + (secondLineFirstWord.length / 2).coerceAtLeast(1),
            9f,
        )

        val secondLine = visibleLayout.lines[1]
        val firstText = secondLine.fragments
            .filterIsInstance<UiTextRun>()
            .first()
        assertEquals(0f, secondLine.x)
        assertEquals(0f, firstText.x)
    }

    @Test
    fun `fit row with max width lays wrapped text in constrained content box`() {
        val root = BoxNode(
            id = "root",
            layout = UiLayout.Row,
            modifiers = listOf(
                Modifier.size(),
                Modifier.minSize(30.px, 16.px),
                Modifier.maxSize(300.px),
                Modifier.padding(4.px),
                Modifier.gap(5.px),
                Modifier.alignItems(vertical = UiAlign.CENTER),
            )
        )
        val icon = ImageNode(
            source = "hollowengine:textures/gui/icons/logo.png".bound(),
            id = "dialogue-icon",
            modifiers = listOf(Modifier.size(16.px, 16.px)),
        )
        val message = TextNode(
            content = dialogueContent(),
            id = "dialogue-message",
            modifiers = listOf(
                Modifier.fontSize(9f),
                Modifier.textWrap(true),
                Modifier.textAlign(UiTextAlign.LEFT),
                Modifier.align(
                    horizontal = UiAlign.START,
                    vertical = UiAlign.CENTER,
                ),
                Modifier.typing(UiTyping(400L)),
            )
        )
        root.children += icon
        root.children += message

        val resolved = UiStyleResolver().resolve(root, animate = false)
        val layout = UiLayoutEngine().compute(resolved, width = 1920f, height = 1080f)
        val rootLayout = layout[root]
        val messageLayout = layout[message]
        val constrainedTextLayout = UiTextLayouter.layout(
            richText = message.content.resolve(UiBindingContext()).toRichText(),
            width = DialogueTextWidth,
            height = Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )
        val renderedWidth = constrainedTextLayout.lines.maxOf { it.naturalWidth }

        assertEquals(renderedWidth + 16f + 5f + 8f, rootLayout.rect.width)
        assertEquals(renderedWidth, messageLayout.content.width)

        val textLayout = UiTextLayouter.layout(
            richText = message.content.resolve(UiBindingContext()).toRichText(),
            width = messageLayout.content.width,
            height = messageLayout.content.height,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )
        assertTrue(textLayout.lines.size >= 2)
        assertEquals(0f, textLayout.lines[1].fragments.first().x)
    }

    @Test
    fun `wrapped fit text reports rendered line width instead of wrap limit`() {
        val natural = UiTextLayouter.measure(
            richText = dialogueText(),
            availableWidth = Float.POSITIVE_INFINITY,
            knownWidth = null,
            wrap = false,
            fontSize = 9f,
        )
        val measured = UiTextLayouter.measure(
            richText = dialogueText(),
            availableWidth = DialogueTextWidth,
            knownWidth = null,
            wrap = true,
            fontSize = 9f,
        )
        val layout = UiTextLayouter.layout(
            richText = dialogueText(),
            width = DialogueTextWidth,
            height = Float.POSITIVE_INFINITY,
            wrap = true,
            align = UiTextAlign.LEFT,
            fontSize = 9f,
        )
        val renderedWidth = layout.lines.maxOf { it.naturalWidth }

        assertTrue(natural.width > DialogueTextWidth)
        assertTrue(renderedWidth < DialogueTextWidth)
        assertEquals(renderedWidth, measured.width)
    }

    @Test
    fun `visible line lookup returns only viewport lines`() {
        val layout = UiTextLayouter.layout(
            text = (1..5000).joinToString("\n") { "line $it" },
            width = 320f,
            height = Float.POSITIVE_INFINITY,
            wrap = false,
            align = UiTextAlign.LEFT,
            fontSize = 12f,
            preserveWhitespace = true,
        )
        val targetLine = layout.lines[2500]
        val viewportHeight = targetLine.height * 5f
        val visible = layout.visibleLineItems(targetLine.y + 0.1f, viewportHeight, overscan = 0f).toList()

        assertTrue(visible.size <= 6)
        assertEquals(2500, visible.first().index)
        assertTrue(visible.all { (_, line) -> line.y <= targetLine.y + 0.1f + viewportHeight })
    }

    @Test
    fun `selection rects use source offsets across empty lines`() {
        val text = "alpha\n\nbeta\ngamma"
        val layout = UiTextLayouter.layout(
            text = text,
            width = 320f,
            height = Float.POSITIVE_INFINITY,
            wrap = false,
            align = UiTextAlign.LEFT,
            fontSize = 12f,
            preserveWhitespace = true,
        )
        val betaStart = text.indexOf("beta")
        val rects = layout.selectionRects(betaStart, betaStart + "beta".length, 12f)

        assertEquals(1, rects.size)
        assertEquals(layout.lines[2].y, rects.single().y)
    }

    @Test
    fun `text style shadow is rendered as glyph effect`() {
        val shadow = UiShadow(
            offset = UiVec3(2f, 3f, 0f),
            blur = 1f,
            spread = 4f,
            color = UiColor(0f, 0f, 0f, 0.75f),
        )
        val text = TextNode(
            text = "Shadow".bound(),
            modifiers = listOf(
                Modifier.fontSize(9f),
                Modifier.shadow(shadow),
            ),
        )

        val resolved = UiStyleResolver().resolve(text, animate = false)
        val layout = UiLayoutEngine().compute(resolved, width = 320f, height = 180f)
        val commands = UiCommandRenderer().collect(resolved, layout)
        val textCommand = commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
        val textShadow = textCommand.textEffects.filterIsInstance<Shadow>().single()

        assertTrue(commands.none { it is DrawShadowCommand && it.node == text })
        assertEquals(shadow.offset.x, textShadow.offsetX)
        assertEquals(shadow.offset.y, textShadow.offsetY)
        assertEquals(shadow.blur, textShadow.blur)
        assertEquals(shadow.color, textShadow.color)
    }

    private fun dialogueText(): UiRichText {
        return UiRichText(
            listOf(
                UiInlineItem.Text("["),
                UiInlineItem.Text("???", colorStyle),
                UiInlineItem.Text("]: Тест тест тест настройка настройка настройка все дела да да да"),
            )
        )
    }

    private fun dialogueContent(): UiTextContent {
        return UiTextContent(
            listOf(
                UiTextSegment.Text("[".bound()),
                UiTextSegment.Text("???".bound(), colorStyle),
                UiTextSegment.Text("]: Тест тест тест настройка настройка настройка все дела да да да".bound()),
            )
        )
    }

    private companion object {
        const val DialogueTextWidth = 271f
        val colorStyle = UiInlineStyle()
            .withColor(UiColor(1f, 0.53f, 0.06f, 1f))
    }
}
