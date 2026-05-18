import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUi
import ru.hollowhorizon.hollowengine.client.ui.HollowUiFrame
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiRect
import ru.hollowhorizon.hollowengine.client.ui.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.UiScope
import ru.hollowhorizon.hollowengine.client.ui.UiTextAlign
import ru.hollowhorizon.hollowengine.client.ui.UiTransformPivot
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val Glyph = 6f
private const val Line = 10f

class UiLayoutContractTests {
    @Test
    fun `SZ-01 fit width equals widest short text child`() {
        lateinit var container: BoxNode
        lateinit var text: TextNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            container = Box(modifier = Modifier.size(UiLength.Auto, UiLength.Auto)) {
                text = Text("Hello", modifier = Modifier.textWrap(false))
            }
        }

        val frame = HollowUiRuntime().frame(root, 1000f, 300f)

        assertRect(frame[container], width = 5 * Glyph, height = Line)
        assertChildInside(frame[container], frame[text])
    }

    @Test
    fun `SZ-01 fit width equals widest long text child`() {
        val sentence = (1..50).joinToString(" ") { "word" }
        lateinit var container: BoxNode
        lateinit var text: TextNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            container = Box(modifier = Modifier.size(UiLength.Auto, UiLength.Auto)) {
                text = Text(sentence, modifier = Modifier.textWrap(false))
            }
        }

        val frame = HollowUiRuntime().frame(root, 4000f, 300f)

        assertRect(frame[container], width = sentence.length * Glyph, height = Line)
        assertChildInside(frame[container], frame[text])
    }

    @Test
    fun `SZ-01 fit width equals widest image-sized child`() {
        lateinit var container: BoxNode
        lateinit var image: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            container = Box(modifier = Modifier.size(UiLength.Auto, UiLength.Auto)) {
                image = Box(modifier = Modifier.size(200.px, 100.px))
            }
        }

        val frame = HollowUiRuntime().frame(root, 1000f, 300f)

        assertRect(frame[container], width = 200f, height = 100f)
        assertChildInside(frame[container], frame[image])
    }

    @Test
    fun `SZ-02 fit height grows with text lines`() {
        for (count in 1..20) {
            lateinit var container: BoxNode
            val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                container = Box(modifier = Modifier.size(200.px, UiLength.Auto)) {
                    repeat(count) { Text("Line $it") }
                }
            }

            val frame = HollowUiRuntime().frame(root, 300f, 500f)

            assertRect(frame[container], width = 200f, height = count * Line)
            container.children.forEach { child -> assertChildInside(frame[container], frame[child]) }
        }
    }

    @Test
    fun `SZ-03 fill occupies parent content in column row and grid`() {
        listOf(LayoutType.COLUMN, LayoutType.ROW, LayoutType.GRID).forEach { layout ->
            lateinit var child: BoxNode
            val root = HollowUi(
                modifier = Modifier.then(
                    Modifier.layout(layout),
                    Modifier.size(400.px, 300.px),
                    Modifier.padding(20.px),
                ),
            ) {
                child = Box(modifier = Modifier.size(UiLength.Fill, UiLength.Fill))
            }

            val frame = HollowUiRuntime().frame(root, 400f, 300f)

            assertRect(frame[child], x = 20f, y = 20f, width = 360f, height = 260f)
        }
    }

    @Test
    fun `SZ-04 px size ignores parent resize and can overflow`() {
        listOf(800f, 400f, 150f, 800f).forEach { parentWidth ->
            lateinit var child: BoxNode
            val root = HollowUi(modifier = Modifier.size(parentWidth.px, 120.px)) {
                child = Box(modifier = Modifier.size(200.px, 100.px))
            }

            val frame = HollowUiRuntime().frame(root, parentWidth, 120f)

            assertRect(frame[child], width = 200f, height = 100f)
            if (parentWidth == 150f) assertTrue(frame[child].x + frame[child].width > frame[root].x + frame[root].width)
        }
    }

    @Test
    fun `SZ-05 percent width recalculates on every parent resize`() {
        for (step in 0..10) {
            val parentWidth = 1000f - 60f * step
            lateinit var child: BoxNode
            val root = HollowUi(modifier = Modifier.size(parentWidth.px, 100.px)) {
                child = Box(modifier = Modifier.size(50.percent, 20.px))
            }

            val frame = HollowUiRuntime().frame(root, parentWidth, 100f)

            assertRect(frame[child], width = parentWidth * 0.5f, height = 20f)
        }
    }

    @Test
    fun `SZ-06 min and max constrain fill`() {
        lateinit var maxed: BoxNode
        val maxRoot = HollowUi(modifier = Modifier.size(800.px, 100.px)) {
            maxed = Box(modifier = Modifier.then(Modifier.size(UiLength.Fill, 20.px), Modifier.maxSize(400.px)))
        }

        lateinit var mined: BoxNode
        val minRoot = HollowUi(modifier = Modifier.size(100.px, 100.px)) {
            mined = Box(modifier = Modifier.then(Modifier.size(UiLength.Fill, 20.px), Modifier.minSize(200.px)))
        }

        assertRect(HollowUiRuntime().frame(maxRoot, 800f, 100f)[maxed], width = 400f)
        assertRect(HollowUiRuntime().frame(minRoot, 100f, 100f)[mined], width = 200f)
    }

    @Test
    fun `SZ-07 fit width in stretched row keeps content width but stretches height`() {
        lateinit var item: BoxNode
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(400.px, 100.px),
                Modifier.alignItems(UiAlign.START, UiAlign.STRETCH),
            ),
        ) {
            item = Box(modifier = Modifier.size(UiLength.Auto, UiLength.Auto)) {
                text = Text("Label", modifier = Modifier.textWrap(false))
            }
        }

        val frame = HollowUiRuntime().frame(root, 400f, 100f)

        assertRect(frame[item], width = 5 * Glyph, height = 100f)
        assertRect(frame[text], width = 5 * Glyph)
    }

    @Test
    fun `SZ-08 nested fill in fill is stable`() {
        val nodes = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.size(500.px, 500.px)) {
            fun UiScope.build(level: Int) {
                val node = Box(modifier = Modifier.size(UiLength.Fill, UiLength.Fill)) {
                    if (level < 5) build(level + 1)
                }
                nodes += node
            }
            build(1)
        }

        val frame = HollowUiRuntime().frame(root, 500f, 500f)

        nodes.forEach { assertRect(frame[it], width = 500f, height = 500f) }
    }

    @Test
    fun `SZ-09 px child expands fit parent`() {
        lateinit var parent: BoxNode
        lateinit var child: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            parent = Box(modifier = Modifier.size(UiLength.Auto, UiLength.Auto)) {
                child = Box(modifier = Modifier.size(300.px, 40.px))
            }
        }

        val frame = HollowUiRuntime().frame(root, 500f, 100f)

        assertRect(frame[parent], width = 300f, height = 40f)
        assertChildInside(frame[parent], frame[child])
    }

    @Test
    fun `SZ-10 zero px does not affect siblings and subpixel px is preserved`() {
        lateinit var zero: BoxNode
        lateinit var subpixel: BoxNode
        lateinit var sibling: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.ROW)) {
            zero = Box(modifier = Modifier.size(0.px, 0.px))
            subpixel = Box(modifier = Modifier.size(0.5.px, 0.5.px))
            sibling = Box(modifier = Modifier.size(10.px, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 100f, 20f)

        assertRect(frame[zero], width = 0f, height = 0f)
        assertRect(frame[subpixel], x = 0f, width = 0.5f, height = 0.5f)
        assertRect(frame[sibling], x = 0.5f, width = 10f, height = 10f)
    }

    @Test
    fun `LY-01 row children arrange horizontally`() {
        val children = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.then(Modifier.layout(LayoutType.ROW), Modifier.size(600.px, 100.px))) {
            repeat(4) { children += Box(modifier = Modifier.size(100.px, 20.px)) }
        }

        val frame = HollowUiRuntime().frame(root, 600f, 100f)

        children.forEachIndexed { index, child -> assertRect(frame[child], x = index * 100f, y = 0f) }
    }

    @Test
    fun `LY-02 column children arrange vertically`() {
        val children = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.then(Modifier.layout(LayoutType.COLUMN), Modifier.size(100.px, 400.px))) {
            repeat(4) { children += Box(modifier = Modifier.size(20.px, 80.px)) }
        }

        val frame = HollowUiRuntime().frame(root, 100f, 400f)

        children.forEachIndexed { index, child -> assertRect(frame[child], x = 0f, y = index * 80f) }
    }

    @Test
    fun `LY-06 nested row inside column fills width and preserves vertical flow`() {
        lateinit var top: TextNode
        lateinit var row: BoxNode
        lateinit var bottom: TextNode
        val rowChildren = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.then(Modifier.layout(LayoutType.COLUMN), Modifier.size(500.px, 200.px))) {
            top = Text("Top")
            row = Box(modifier = Modifier.layout(LayoutType.ROW)) {
                repeat(3) { rowChildren += Box(modifier = Modifier.size(100.px, 20.px)) }
            }
            bottom = Text("Bottom")
        }

        val frame = HollowUiRuntime().frame(root, 500f, 200f)

        assertRect(frame[top], y = 0f, height = Line)
        assertRect(frame[row], x = 0f, y = Line, width = 500f, height = 20f)
        assertRect(frame[bottom], y = Line + 20f)
        rowChildren.forEachIndexed { index, child -> assertRect(frame[child], x = index * 100f, y = Line) }
    }

    @Test
    fun `AL-01 justify start center end on row main axis`() {
        listOf(UiAlign.START to 0f, UiAlign.CENTER to 150f, UiAlign.END to 300f).forEach { (align, startX) ->
            val children = mutableListOf<BoxNode>()
            val root = HollowUi(
                modifier = Modifier.then(
                    Modifier.layout(LayoutType.ROW),
                    Modifier.size(600.px, 100.px),
                    Modifier.alignItems(align, UiAlign.START),
                ),
            ) {
                repeat(3) { children += Box(modifier = Modifier.size(100.px, 20.px)) }
            }

            val frame = HollowUiRuntime().frame(root, 600f, 100f)

            children.forEachIndexed { index, child -> assertRect(frame[child], x = startX + index * 100f) }
        }
    }

    @Test
    fun `AL-02 align items start center end on row cross axis`() {
        val heights = listOf(40, 80, 120)
        listOf(
            UiAlign.START to listOf(0f, 0f, 0f),
            UiAlign.CENTER to listOf(80f, 60f, 40f),
            UiAlign.END to listOf(160f, 120f, 80f),
        ).forEach { (align, expectedY) ->
            val children = mutableListOf<BoxNode>()
            val root = HollowUi(
                modifier = Modifier.then(
                    Modifier.layout(LayoutType.ROW),
                    Modifier.size(600.px, 200.px),
                    Modifier.alignItems(UiAlign.START, align),
                ),
            ) {
                heights.forEach { children += Box(modifier = Modifier.size(100.px, it.px)) }
            }

            val frame = HollowUiRuntime().frame(root, 600f, 200f)

            children.forEachIndexed { index, child -> assertRect(frame[child], y = expectedY[index]) }
        }
    }

    @Test
    fun `AL-03 align self overrides row align items`() {
        lateinit var a: BoxNode
        lateinit var b: BoxNode
        lateinit var c: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(600.px, 200.px),
                Modifier.alignItems(UiAlign.START, UiAlign.START),
            ),
        ) {
            a = Box(modifier = Modifier.size(100.px, 40.px))
            b = Box(modifier = Modifier.then(Modifier.size(100.px, 40.px), Modifier.align(UiAlign.AUTO, UiAlign.END)))
            c = Box(modifier = Modifier.then(Modifier.size(100.px, 40.px), Modifier.align(UiAlign.AUTO, UiAlign.CENTER)))
        }

        val frame = HollowUiRuntime().frame(root, 600f, 200f)

        assertRect(frame[a], y = 0f)
        assertRect(frame[b], y = 160f)
        assertRect(frame[c], y = 80f)
    }

    @Test
    fun `AL-04 single child centered on both axes`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(400.px, 400.px),
                Modifier.alignItems(UiAlign.CENTER, UiAlign.CENTER),
            ),
        ) {
            child = Box(modifier = Modifier.size(100.px, 100.px))
        }

        val frame = HollowUiRuntime().frame(root, 400f, 400f)

        assertRect(frame[child], x = 150f, y = 150f, width = 100f, height = 100f)
    }

    @Test
    fun `AL-10 root align positions styled root inside viewport`() {
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(200.px, 100.px),
                Modifier.align(UiAlign.CENTER, UiAlign.CENTER),
            ),
        )

        val frame = HollowUiRuntime().frame(root, 600f, 400f)

        assertRect(frame[root], x = 200f, y = 150f, width = 200f, height = 100f)
    }

    @Test
    fun `SP-01 padding reduces content area without changing border-box size`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(200.px, 100.px),
                Modifier.padding(20.px),
            ),
        ) {
            child = Box(modifier = Modifier.size(UiLength.Fill, UiLength.Fill))
        }

        val frame = HollowUiRuntime().frame(root, 200f, 100f)

        assertRect(frame[root], width = 200f, height = 100f)
        assertRect(frame.layout[root].content, x = 20f, y = 20f, width = 160f, height = 60f)
        assertRect(frame[child], x = 20f, y = 20f, width = 160f, height = 60f)
    }

    @Test
    fun `SP-03 gap is applied only between row children`() {
        val children = mutableListOf<BoxNode>()
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(148.px, 30.px),
                Modifier.gap(16.px),
            ),
        ) {
            repeat(4) { children += Box(modifier = Modifier.size(25.px, 10.px)) }
        }

        val frame = HollowUiRuntime().frame(root, 148f, 30f)

        listOf(0f, 41f, 82f, 123f).forEachIndexed { index, x -> assertRect(frame[children[index]], x = x) }
        assertEquals(16f, frame[children[1]].x - (frame[children[0]].x + frame[children[0]].width), 0.01f)
        assertEquals(148f, frame[children.last()].x + frame[children.last()].width, 0.01f)
    }

    @Test
    fun `SP-06 nested padding values accumulate`() {
        lateinit var inner: BoxNode
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(200.px, 100.px),
                Modifier.padding(16.px),
            ),
        ) {
            inner = Box(modifier = Modifier.padding(8.px)) {
                text = Text("Text")
            }
        }

        val frame = HollowUiRuntime().frame(root, 200f, 100f)

        assertRect(frame[inner], x = 16f, y = 16f)
        assertRect(frame[text], x = 24f, y = 24f)
    }

    @Test
    fun `BD-01 border does not change declared outer size`() {
        lateinit var bordered: BoxNode
        lateinit var content: BoxNode
        lateinit var sibling: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.ROW)) {
            bordered = Box(
                modifier = Modifier.then(
                    Modifier.size(200.px, 100.px),
                    Modifier.border(2.px, UiColor.White),
                ),
            ) {
                content = Box(modifier = Modifier.size(UiLength.Fill, UiLength.Fill))
            }
            sibling = Box(modifier = Modifier.size(50.px, 100.px))
        }

        val frame = HollowUiRuntime().frame(root, 300f, 120f)

        assertRect(frame[bordered], x = 0f, width = 200f, height = 100f)
        assertRect(frame[content], x = 2f, y = 2f, width = 196f, height = 96f)
        assertRect(frame[sibling], x = 200f, width = 50f)
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it.node == bordered && it is DrawBoxCommand })
        assertEquals(2f, draw.border.width.left.resolve(200f, 100f), 0.01f)
    }

    @Test
    fun `CL-02 overflow visible keeps protruding child unclipped`() {
        lateinit var parent: BoxNode
        lateinit var child: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            parent = Box(modifier = Modifier.then(Modifier.layout(LayoutType.FREE), Modifier.size(100.px, 50.px))) {
                child = Box(modifier = Modifier.size(200.px, 20.px))
            }
        }

        val frame = HollowUiRuntime().frame(root, 300f, 100f)

        assertRect(frame[parent], width = 100f)
        assertRect(frame[child], width = 200f)
        assertNull(frame.layout[child].clip)
    }

    @Test
    fun `CL-07 scrollable container reveals last item and bottom padding`() {
        lateinit var scrollable: BoxNode
        val items = mutableListOf<BoxNode>()
        val scrollState = UiScrollState()
        val runtime = HollowUiRuntime(scrollState = scrollState)
        val root = HollowUi {
            scrollable = Box(
                modifier = Modifier.then(
                    Modifier.size(100.px, 300.px),
                    Modifier.padding(0.px, 0.px, 0.px, 24.px),
                    Modifier.input(scrollable = true),
                ),
            ) {
                repeat(10) { items += Box(modifier = Modifier.size(100.px, 50.px)) }
            }
        }

        runtime.frame(root, 100f, 300f)
        runtime.setScrollImmediate(scrollable, y = 224f)
        val frame = runtime.frame(root, 100f, 300f)
        val vertical = assertIs<DrawScrollbarCommand>(
            frame.commands.first { it is DrawScrollbarCommand && it.orientation == ScrollbarOrientation.VERTICAL }
        )

        assertRect(frame[items.last()], y = 226f, height = 50f)
        assertEquals(frame.layout[scrollable].content.y + frame.layout[scrollable].content.height, frame[items.last()].y + frame[items.last()].height, 0.01f)
        assertEquals(24f, frame[scrollable].y + frame[scrollable].height - (frame[items.last()].y + frame[items.last()].height), 0.01f)
        assertTrue(vertical.thumb.height < vertical.track.height)
    }

    @Test
    fun `TR-02 scale does not change row layout space`() {
        val children = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.layout(LayoutType.ROW)) {
            children += Box(modifier = Modifier.size(100.px, 50.px))
            children += Box(modifier = Modifier.then(Modifier.size(100.px, 50.px), Modifier.scale(2f)))
            children += Box(modifier = Modifier.size(100.px, 50.px))
        }

        val frame = HollowUiRuntime().frame(root, 400f, 80f)

        children.forEachIndexed { index, child -> assertRect(frame[child], x = index * 100f, width = 100f) }
    }

    @Test
    fun `TR-03 rotation does not change column layout space`() {
        val children = mutableListOf<BoxNode>()
        val root = HollowUi(modifier = Modifier.layout(LayoutType.COLUMN)) {
            children += Box(modifier = Modifier.size(100.px, 100.px))
            children += Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.rotate(z = 45f)))
            children += Box(modifier = Modifier.size(100.px, 100.px))
        }

        val frame = HollowUiRuntime().frame(root, 200f, 400f)

        children.forEachIndexed { index, child -> assertRect(frame[child], y = index * 100f, height = 100f) }
    }

    @Test
    fun `CM-03 percent padding and border in border-box expose exact content width`() {
        lateinit var element: BoxNode
        lateinit var content: BoxNode
        val root = HollowUi(modifier = Modifier.size(600.px, 100.px)) {
            element = Box(
                modifier = Modifier.then(
                    Modifier.size(50.percent, 80.px),
                    Modifier.padding(20.px),
                    Modifier.border(2.px, UiColor.White),
                ),
            ) {
                content = Box(modifier = Modifier.size(UiLength.Fill, UiLength.Fill))
            }
        }

        val frame = HollowUiRuntime().frame(root, 600f, 100f)

        assertRect(frame[element], width = 300f, height = 80f)
        assertRect(frame.layout[element].content, width = 256f, height = 36f)
        assertRect(frame[content], x = 22f, y = 22f, width = 256f, height = 36f)
    }

    @Test
    fun `CM-09 row stretch uses tallest mixed-height child`() {
        val children = mutableListOf<BoxNode>()
        lateinit var row: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            row = Box(
                modifier = Modifier.then(
                    Modifier.layout(LayoutType.ROW),
                    Modifier.size(300.px, UiLength.Auto),
                    Modifier.alignItems(UiAlign.START, UiAlign.STRETCH),
                ),
            ) {
                children += Box(modifier = Modifier.size(80.px, UiLength.Auto)) {
                    Box(modifier = Modifier.size(80.px, 20.px))
                }
                children += Box(modifier = Modifier.size(80.px, UiLength.Auto)) {
                    Box(modifier = Modifier.size(80.px, 50.px))
                }
                children += Box(modifier = Modifier.then(Modifier.size(80.px, UiLength.Auto), Modifier.padding(24.px))) {
                    Text("A")
                }
            }
        }

        val frame = HollowUiRuntime().frame(root, 300f, 100f)

        assertRect(frame[row], height = 58f)
        children.forEach { assertRect(frame[it], height = 58f) }
    }

    @Test
    fun `TR-P1 scale uses explicit pivot point`() {
        listOf(
            UiTransformPivot.Center to UiVec3(-50f, -50f, 0f),
            UiTransformPivot.TopLeft to UiVec3(0f, 0f, 0f),
            UiTransformPivot.BottomRight to UiVec3(-100f, -100f, 0f),
        ).forEach { (pivot, expectedTopLeft) ->
            lateinit var child: BoxNode
            val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                child = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot(pivot), Modifier.scale(2f)))
            }

            val frame = HollowUiRuntime().frame(root, 200f, 200f)

            assertVec(frame.layout[child].worldTransform.transform(0f, 0f), expectedTopLeft)
        }
    }

    @Test
    fun `TR-P2 rotation keeps the chosen pivot fixed`() {
        listOf(
            UiTransformPivot.Center to UiVec3(50f, 50f, 0f),
            UiTransformPivot.TopLeft to UiVec3(0f, 0f, 0f),
            UiTransformPivot.BottomRight to UiVec3(100f, 100f, 0f),
        ).forEach { (pivot, fixedPoint) ->
            lateinit var child: BoxNode
            val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                child = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot(pivot), Modifier.rotate(z = 45f)))
            }

            val frame = HollowUiRuntime().frame(root, 200f, 200f)

            assertVec(frame.layout[child].worldTransform.transform(fixedPoint.x, fixedPoint.y), fixedPoint)
        }
    }

    @Test
    fun `TR-P3 translate is independent from pivot`() {
        listOf(UiTransformPivot.TopLeft, UiTransformPivot.Center, UiTransformPivot.BottomRight).forEach { pivot ->
            lateinit var child: BoxNode
            val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                child = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot(pivot), Modifier.translate(x = 100f)))
            }

            val frame = HollowUiRuntime().frame(root, 200f, 200f)

            assertVec(frame.layout[child].worldTransform.transform(0f, 0f), UiVec3(100f, 0f, 0f))
        }
    }

    @Test
    fun `TR-P4 numeric pivot matches equivalent named shorthand and outside pivot orbits`() {
        lateinit var named: BoxNode
        lateinit var numeric: BoxNode
        lateinit var outside: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            named = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot(UiTransformPivot.TopLeft), Modifier.rotate(z = 45f)))
            numeric = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot(0.px, 0.px), Modifier.rotate(z = 45f)))
            outside = Box(modifier = Modifier.then(Modifier.size(100.px, 100.px), Modifier.pivot((-50).px, (-50).px), Modifier.rotate(z = 90f)))
        }

        val frame = HollowUiRuntime().frame(root, 300f, 300f)

        assertVec(frame.layout[named].worldTransform.transform(100f, 0f), frame.layout[numeric].worldTransform.transform(100f, 0f))
        assertVec(frame.layout[outside].worldTransform.transform(0f, 0f), UiVec3(-100f, 0f, 0f))
    }

    @Test
    fun `TR-P5 rotateX with perspective remains horizontally symmetric`() {
        lateinit var child: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            child = Box(
                modifier = Modifier.then(
                    Modifier.size(200.px, 100.px),
                    Modifier.pivot(UiTransformPivot.Center),
                    Modifier.rotate(x = 25f),
                    Modifier.perspective(300f),
                ),
            )
        }

        val transform = HollowUiRuntime().frame(root, 300f, 200f).layout[child].worldTransform
        val topLeft = transform.transform(0f, 0f)
        val topRight = transform.transform(200f, 0f)
        val bottomLeft = transform.transform(0f, 100f)
        val bottomRight = transform.transform(200f, 100f)

        assertEquals(topLeft.y, topRight.y, 0.01f)
        assertEquals(bottomLeft.y, bottomRight.y, 0.01f)
        assertEquals(100f, (topLeft.x + topRight.x) / 2f, 0.01f)
        assertEquals(100f, (bottomLeft.x + bottomRight.x) / 2f, 0.01f)
    }

    @Test
    fun `TX-01 text align positions lines inside text box`() {
        listOf(
            UiTextAlign.LEFT to 0f,
            UiTextAlign.CENTER to 35f,
            UiTextAlign.RIGHT to 70f,
        ).forEach { (align, x) ->
            lateinit var text: TextNode
            val root = HollowUi {
                text = Text("Hello", modifier = Modifier.then(Modifier.size(100.px, UiLength.Auto), Modifier.textAlign(align)))
            }

            val command = HollowUiRuntime().frame(root, 100f, 50f).textCommand(text)

            assertEquals(x, command.layout.lines.single().x, 0.01f)
            assertEquals(30f, command.layout.lines.single().naturalWidth, 0.01f)
        }
    }

    @Test
    fun `TX-02 justify excludes last one-word and explicit-break lines`() {
        lateinit var justified: TextNode
        lateinit var oneWord: TextNode
        lateinit var forced: TextNode
        val root = HollowUi {
            justified = Text("aa bb cc dd ee", modifier = Modifier.then(Modifier.size(48.px, UiLength.Auto), Modifier.textAlign(UiTextAlign.JUSTIFY)))
            oneWord = Text("longword", modifier = Modifier.then(Modifier.size(20.px, UiLength.Auto), Modifier.textAlign(UiTextAlign.JUSTIFY)))
            forced = Text("aa bb\ncc dd", modifier = Modifier.then(Modifier.size(100.px, UiLength.Auto), Modifier.textAlign(UiTextAlign.JUSTIFY)))
        }

        val frame = HollowUiRuntime().frame(root, 120f, 120f)

        assertTrue(frame.textCommand(justified).layout.lines.first().justify)
        assertTrue(frame.textCommand(justified).layout.lines.last().justify.not())
        assertTrue(frame.textCommand(oneWord).layout.lines.all { !it.justify && it.x == 0f })
        assertTrue(frame.textCommand(forced).layout.lines.all { !it.justify && it.x == 0f })
    }

    @Test
    fun `TX-03 alignment does not change line breaks and inherits from container`() {
        val content = "aa bb cc dd ee"
        val lineSets = UiTextAlign.entries.map { align ->
            lateinit var text: TextNode
            val root = HollowUi {
                text = Text(content, modifier = Modifier.then(Modifier.size(48.px, UiLength.Auto), Modifier.textAlign(align)))
            }
            HollowUiRuntime().frame(root, 100f, 100f).textCommand(text).layout.lines.map { it.text }
        }
        lateinit var inherited: TextNode
        val inheritedRoot = HollowUi(modifier = Modifier.textAlign(UiTextAlign.RIGHT)) {
            inherited = Text("Hello", modifier = Modifier.size(100.px, UiLength.Auto))
        }

        assertEquals(1, lineSets.distinct().size)
        assertEquals(70f, HollowUiRuntime().frame(inheritedRoot, 100f, 50f).textCommand(inherited).layout.lines.single().x, 0.01f)
    }

    @Test
    fun `TX-04 right aligned text respects padding right`() {
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(100.px, 40.px),
                Modifier.padding(0.px, 0.px, 20.px, 0.px),
                Modifier.textAlign(UiTextAlign.RIGHT),
            ),
        ) {
            text = Text("Hello", modifier = Modifier.size(UiLength.Fill, UiLength.Auto))
        }

        val frame = HollowUiRuntime().frame(root, 100f, 40f)
        val line = frame.textCommand(text).layout.lines.single()

        assertEquals(80f, frame[text].x + line.x + line.naturalWidth, 0.01f)
    }

    @Test
    fun `SC-01 transform scale never changes text layout snapshot`() {
        val content = "aa bb cc dd ee"
        lateinit var normal: TextNode
        lateinit var scaledUp: TextNode
        lateinit var scaledDown: TextNode
        val root = HollowUi {
            normal = Text(content, modifier = Modifier.size(48.px, UiLength.Auto))
            scaledUp = Text(content, modifier = Modifier.then(Modifier.size(48.px, UiLength.Auto), Modifier.scale(2f)))
            scaledDown = Text(content, modifier = Modifier.then(Modifier.size(48.px, UiLength.Auto), Modifier.scale(0.5f)))
        }

        val frame = HollowUiRuntime().frame(root, 100f, 120f)
        val expectedLines = frame.textCommand(normal).layout.lines.map { it.text }

        assertRect(frame[normal], width = 48f, height = 20f)
        assertRect(frame[scaledUp], width = 48f, height = 20f)
        assertRect(frame[scaledDown], width = 48f, height = 20f)
        assertEquals(expectedLines, frame.textCommand(scaledUp).layout.lines.map { it.text })
        assertEquals(expectedLines, frame.textCommand(scaledDown).layout.lines.map { it.text })
    }

    @Test
    fun `SC-02 font size changes reflow and sibling position while scale does not`() {
        lateinit var normal: TextNode
        lateinit var normalSibling: BoxNode
        lateinit var large: TextNode
        lateinit var largeSibling: BoxNode
        val content = "aa bb cc dd"
        val normalRoot = HollowUi {
            normal = Text(content, modifier = Modifier.size(48.px, UiLength.Auto))
            normalSibling = Box(modifier = Modifier.size(10.px, 10.px))
        }
        val largeRoot = HollowUi {
            large = Text(content, modifier = Modifier.then(Modifier.size(48.px, UiLength.Auto), Modifier.fontSize(20f)))
            largeSibling = Box(modifier = Modifier.size(10.px, 10.px))
        }

        val normalFrame = HollowUiRuntime().frame(normalRoot, 100f, 120f)
        val largeFrame = HollowUiRuntime().frame(largeRoot, 100f, 160f)

        assertTrue(largeFrame[large].height > normalFrame[normal].height)
        assertTrue(largeFrame[largeSibling].y > normalFrame[normalSibling].y)
    }
}

private operator fun HollowUiFrame.get(node: ru.hollowhorizon.hollowengine.client.ui.UiNode): UiRect = layout[node].rect

private fun assertRect(
    actual: UiRect,
    x: Float? = null,
    y: Float? = null,
    width: Float? = null,
    height: Float? = null,
    tolerance: Float = 0.01f,
) {
    x?.let { assertEquals(it, actual.x, tolerance, "x mismatch for $actual") }
    y?.let { assertEquals(it, actual.y, tolerance, "y mismatch for $actual") }
    width?.let { assertEquals(it, actual.width, tolerance, "width mismatch for $actual") }
    height?.let { assertEquals(it, actual.height, tolerance, "height mismatch for $actual") }
}

private fun assertChildInside(parent: UiRect, child: UiRect) {
    assertTrue(child.x >= parent.x, "Child starts before parent: child=$child parent=$parent")
    assertTrue(child.y >= parent.y, "Child starts above parent: child=$child parent=$parent")
    assertTrue(child.x + child.width <= parent.x + parent.width, "Child overflows parent horizontally: child=$child parent=$parent")
    assertTrue(child.y + child.height <= parent.y + parent.height, "Child overflows parent vertically: child=$child parent=$parent")
}

private fun HollowUiFrame.textCommand(node: TextNode): DrawTextCommand {
    return assertIs(commands.first { it.node == node && it is DrawTextCommand })
}

private fun assertVec(actual: UiVec3, expected: UiVec3, tolerance: Float = 0.01f) {
    assertEquals(expected.x, actual.x, tolerance, "x mismatch for $actual")
    assertEquals(expected.y, actual.y, tolerance, "y mismatch for $actual")
    assertEquals(expected.z, actual.z, tolerance, "z mismatch for $actual")
}
