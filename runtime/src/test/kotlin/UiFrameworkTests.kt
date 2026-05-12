import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUi
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class UiFrameworkTests {
    @Test
    fun `hss selectors resolve state rules over base rules`() {
        val stylesheet = compileHss(
            """
            .dialogue-box {
                layout: column;
                padding: 12px;
                gap: 8px;
                opacity: 0.5;
                background: rgba(10, 12, 20, 0.85);
            }

            .dialogue-box:hover {
                scale: 1.02;
                opacity: 1.0;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("dialogue-box")).apply {
            states += UiState.HOVER
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 320f, 180f)
        val style = frame.resolved[root]

        assertEquals(LayoutType.COLUMN, style.layout)
        assertEquals(1f, style.opacity)
        assertEquals(1.02f, style.transform.scale.x)
        assertEquals(12f, (style.padding.left as UiLength.Px).value)
    }

    @Test
    fun `inline modifiers override stylesheet rules`() {
        val stylesheet = compileHss(
            """
            #dialog-root {
                opacity: 0.25;
                width: 10px;
            }
            """.trimIndent()
        )
        val root = HollowUi(
            id = "dialog-root",
            modifier = Modifier.then(Modifier.opacity(0.8f), Modifier.size(50.px, 20.px)),
        )

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f)

        assertEquals(0.8f, frame.resolved[root].opacity)
        assertEquals(50f, (frame.resolved[root].size.width as UiLength.Px).value)
    }

    @Test
    fun `free layout places children by explicit position and propagates hit testing`() {
        lateinit var child: ru.hollowhorizon.hollowengine.client.ui.BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            child = Box(
                id = "quest",
                modifier = Modifier.then(
                    Modifier.position(120.px, 40.percent),
                    Modifier.size(30.px, 20.px),
                    Modifier.input(clickable = true),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 800f, 1000f)
        val rect = frame.layout[child].rect
        val hit = frame.hitTest(121f, 401f)

        assertEquals(120f, rect.x)
        assertEquals(400f, rect.y)
        assertNotNull(hit)
        assertEquals(child, hit.node)
    }

    @Test
    fun `hss bindings resolve compound tag paths in render commands`() {
        val stylesheet = compileHss(
            """
            .portrait {
                background: image("{data.character.icon}");
                size: 32px 32px;
            }
            """.trimIndent()
        )
        val root = HollowUi {
            Box(tags = listOf("portrait"))
        }
        val tag = CompoundTag().apply {
            put("data", CompoundTag().apply {
                put("character", CompoundTag().apply {
                    putString("icon", "hollowengine:textures/gui/npc_menu/character.png")
                })
            })
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f, UiBindingContext(tag))
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it is DrawBoxCommand && it.node.tags.contains("portrait") })
        val paint = assertIs<UiResolvedPaint.Image>(draw.paint)

        assertEquals("hollowengine:textures/gui/npc_menu/character.png", paint.source)
    }

    @Test
    fun `border parser preserves rgba functions with spaces`() {
        val stylesheet = compileHss(
            """
            .card {
                border: 1px rgba(120, 140, 170, 0.38);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("card"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f)

        assertEquals(1f, (frame.resolved[root].border.width.left as UiLength.Px).value)
        assertEquals(120f / 255f, frame.resolved[root].border.color.red)
        assertEquals(0.38f, frame.resolved[root].border.color.alpha)
    }

    @Test
    fun `scrollable nodes emit scrollbars and offset children`() {
        lateinit var scroller: ru.hollowhorizon.hollowengine.client.ui.BoxNode
        lateinit var row: ru.hollowhorizon.hollowengine.client.ui.BoxNode
        val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
            scroller = Box(
                tags = listOf("scroll"),
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.input(scrollable = true),
                ),
            ) {
                row = Box(modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(80.px, 30.px)))
            }
        }
        val runtime = HollowUiRuntime()

        val initial = runtime.frame(root, 120f, 80f)
        runtime.setScrollImmediate(scroller, y = 24f)
        val scrolled = runtime.frame(root, 120f, 80f)

        assertEquals(true, initial.commands.any { it is DrawScrollbarCommand })
        assertEquals(initial.layout[row].rect.y - 24f, scrolled.layout[row].rect.y)
        assertEquals(scrolled.layout[row].rect.y, scrolled.layout[row].worldTransform.transform(0f, 0f).y)
    }

    @Test
    fun `scroll state survives rebuilt ui tree by stable node id`() {
        data class ScrollTree(
            val root: ru.hollowhorizon.hollowengine.client.ui.BoxNode,
            val scroller: ru.hollowhorizon.hollowengine.client.ui.BoxNode,
            val row: ru.hollowhorizon.hollowengine.client.ui.BoxNode,
        )

        fun tree(): ScrollTree {
            lateinit var scroller: ru.hollowhorizon.hollowengine.client.ui.BoxNode
            lateinit var row: ru.hollowhorizon.hollowengine.client.ui.BoxNode
            val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
                scroller = Box(
                    id = "stable-scroll",
                    modifier = Modifier.then(Modifier.size(100.px, 40.px), Modifier.input(scrollable = true)),
                ) {
                    row = Box(id = "stable-row", modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(80.px, 30.px)))
                }
            }
            return ScrollTree(root, scroller, row)
        }

        val runtime = HollowUiRuntime()
        val firstTree = tree()
        val first = runtime.frame(firstTree.root, 120f, 80f)
        runtime.setScrollImmediate(firstTree.scroller, y = 24f)
        val secondTree = tree()
        val second = runtime.frame(secondTree.root, 120f, 80f)

        assertEquals(first.layout[firstTree.row].rect.y - 24f, second.layout[secondTree.row].rect.y)
    }

    @Test
    fun `transitions survive rebuilt ui tree by stable node id`() {
        val stylesheet = compileHss(
            """
            #button {
                opacity: 0.2;
                transition: opacity 100ms linear;
            }

            #button:hover {
                opacity: 1.0;
            }
            """.trimIndent()
        )
        fun button(hovered: Boolean) = HollowUi(id = "button").apply {
            if (hovered) states += UiState.HOVER
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(button(false), 100f, 40f, nowMillis = 0L)
        runtime.frame(button(true), 100f, 40f, nowMillis = 50L)
        val half = runtime.frame(button(true), 100f, 40f, nowMillis = 100L)

        assertEquals(0.6f, half.resolved[half.resolved.root].opacity, 0.01f)
    }
}
