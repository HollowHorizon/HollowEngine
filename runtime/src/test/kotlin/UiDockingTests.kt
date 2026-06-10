import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.*
import kotlin.test.*

class UiDockingTests {
    @Test
    fun `opening items stacks them as tabs by default`() {
        val state = DockingState()

        state.open(item("files"))
        state.open(item("console"))

        val stack = state.root as DockNode.Stack
        assertEquals(listOf("files", "console"), stack.items.map { it.id })
        assertEquals("console", stack.selectedItemId)
        assertEquals("console", state.focusedItemId)
    }

    @Test
    fun `docking item to side creates split and preserves stack selection`() {
        val state = DockingState()

        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))

        val split = state.root as DockNode.Split
        assertEquals(DockOrientation.HORIZONTAL, split.orientation)
        assertEquals("files", (split.first as DockNode.Stack).selectedItemId)
        assertEquals("console", (split.second as DockNode.Stack).selectedItemId)
    }

    @Test
    fun `closing last tab collapses parent split`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))

        assertTrue(state.close("console"))

        val stack = state.root as DockNode.Stack
        assertEquals(listOf("files"), stack.items.map { it.id })
    }

    @Test
    fun `undocked item can be docked back into a stack`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))

        assertTrue(state.undockToFloating("console", x = 100f, y = 80f))
        assertEquals(listOf("files"), (state.root as DockNode.Stack).items.map { it.id })
        val window = state.floatingWindows.single()

        assertTrue(state.dockWindow(window.id, DockTarget(placement = DockPlacement.CENTER)))

        val stack = state.root as DockNode.Stack
        assertEquals(listOf("files", "console"), stack.items.map { it.id })
        assertEquals("console", stack.selectedItemId)
        assertTrue(state.floatingWindows.isEmpty())
    }

    @Test
    fun `split resize clamps fraction`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))
        val splitId = (state.root as DockNode.Split).id

        assertTrue(state.setSplitFraction(splitId, 5f))
        assertEquals(0.9f, (state.root as DockNode.Split).fraction)

        assertTrue(state.setSplitFraction(splitId, -1f))
        assertEquals(0.1f, (state.root as DockNode.Split).fraction)
    }

    @Test
    fun `root split resize preserves nested same-orientation boundaries`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))
        state.open(item("logs"), DockTarget(placement = DockPlacement.RIGHT))
        val rootSplit = state.root as DockNode.Split
        val before = DockLayoutCalculator.layout(state.root, DockRect(0f, 0f, 100f, 100f))
            .first { it.nodeId == "dock-stack-1" }
            .rect

        assertTrue(state.setSplitFraction(rootSplit.id, 0.7f))

        val after = DockLayoutCalculator.layout(state.root, DockRect(0f, 0f, 100f, 100f))
            .first { it.nodeId == "dock-stack-1" }
            .rect
        assertEquals(before.right, after.right)
    }

    @Test
    fun `edge split resize does not move unrelated boundaries in a row`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))
        state.open(item("logs"), DockTarget(placement = DockPlacement.RIGHT))
        state.open(item("preview"), DockTarget(placement = DockPlacement.RIGHT))
        val firstSplit = "dock-split-1"
        val lastSplit = (state.root as DockNode.Split).id
        val before = stackBounds(state)

        assertTrue(state.setSplitFraction(firstSplit, 0.8f))
        val afterFirst = stackBounds(state)
        assertEquals(before.getValue("dock-stack-2").right, afterFirst.getValue("dock-stack-2").right)
        assertEquals(before.getValue("dock-stack-3").right, afterFirst.getValue("dock-stack-3").right)

        assertTrue(state.setSplitFraction(lastSplit, 0.7f))
        val afterLast = stackBounds(state)
        assertEquals(afterFirst.getValue("dock-stack-1").right, afterLast.getValue("dock-stack-1").right)
        assertEquals(afterFirst.getValue("dock-stack-2").right, afterLast.getValue("dock-stack-2").right)
    }

    @Test
    fun `middle split resize only moves adjacent row boundary`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))
        state.open(item("logs"), DockTarget(placement = DockPlacement.RIGHT))
        state.open(item("preview"), DockTarget(placement = DockPlacement.RIGHT))
        val before = stackBounds(state)

        assertTrue(state.setSplitFraction("dock-split-2", 0.7f))

        val after = stackBounds(state)
        assertEquals(before.getValue("dock-stack-1").right, after.getValue("dock-stack-1").right)
        assertEquals(before.getValue("dock-stack-4").x, after.getValue("dock-stack-4").x)
    }

    @Test
    fun `floating windows resize from left and top edges`() {
        val state = DockingState()
        state.openFloating(item("console"), x = 100f, y = 80f, width = 240f, height = 180f)
        val window = state.floatingWindows.single()

        assertTrue(state.resizeFloating(window.id, DockResizeEdge.LEFT, deltaX = 30f, deltaY = 0f))
        assertEquals(130f, state.floatingWindows.single().x)
        assertEquals(210f, state.floatingWindows.single().width)

        assertTrue(state.resizeFloating(window.id, DockResizeEdge.TOP, deltaX = 0f, deltaY = 40f))
        assertEquals(120f, state.floatingWindows.single().y)
        assertEquals(140f, state.floatingWindows.single().height)
    }

    @Test
    fun `floating window header starts drag after press`() {
        val state = DockingState()
        state.openFloating(item("console"), x = 20f, y = 16f, width = 220f, height = 160f)
        val input = HollowUiInputController()

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            input.mouseClicked(frame, mouseX = 36f, mouseY = 24f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})

            assertEquals("dock-window-1-header", input.draggingKey)

            input.mouseDragged(frame, mouseX = 46f, mouseY = 29f, button = 0, deltaX = 10f, deltaY = 5f) {
                it.node.dispatch(it)
            }

            val moved = state.floatingWindows.single()
            assertEquals(30f, moved.x)
            assertEquals(21f, moved.y)
            assertEquals("dock-window-1", state.draggedWindowId)
        }
    }

    @Test
    fun `draggable tabs can be selected by press`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))
        state.select("files")
        val input = HollowUiInputController()

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)

            input.mouseClicked(frame, mouseX = 150f, mouseY = 12f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})

            assertEquals("console", (state.root as DockNode.Stack).selectedItemId)
        }
    }

    @Test
    fun `tabs can be reordered inside tab bar`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))
        state.open(item("logs"))
        val input = HollowUiInputController()

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            input.mouseClicked(frame, mouseX = 150f, mouseY = 12f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})
            input.mouseDragged(frame, mouseX = 10f, mouseY = 12f, button = 0, deltaX = -140f, deltaY = 0f) {
                it.node.dispatch(it)
            }

            assertEquals(listOf("console", "files", "logs"), (state.root as DockNode.Stack).items.map { it.id })
        }
    }

    @Test
    fun `tab reorder waits until dragged edge reaches neighbor midpoint`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))
        state.open(item("logs"))
        val stackId = (state.root as DockNode.Stack).id

        assertFalse(state.dragTabInBar(stackId, "console", pointerX = 219f, grabX = 10f, tabWidth = 140f))
        assertEquals(listOf("files", "console", "logs"), (state.root as DockNode.Stack).items.map { it.id })

        assertTrue(state.dragTabInBar(stackId, "console", pointerX = 220f, grabX = 10f, tabWidth = 140f))
        assertEquals(listOf("files", "logs", "console"), (state.root as DockNode.Stack).items.map { it.id })
    }

    @Test
    fun `undocked tab starts floating in dock space coordinates`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))
        val input = HollowUiInputController()

        HollowComposeUiRuntime().use { runtime ->
            val firstFrame = runtime.frame(
                content = {
                    Box(
                        modifier = Modifier.then(
                            Modifier.layout(LayoutType.FREE),
                            Modifier.size(100.percent, 100.percent),
                        ),
                    ) {
                        DockSpace(
                            state,
                            modifier = Modifier.then(
                                Modifier.position(40.px, 30.px),
                                Modifier.size(500.px, 300.px),
                            ),
                        ) { Text(it.title) }
                    }
                },
                width = 640f,
                height = 360f,
            )
            input.mouseClicked(firstFrame, mouseX = 190f, mouseY = 42f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})
            input.mouseDragged(firstFrame, mouseX = 200f, mouseY = 90f, button = 0, deltaX = 10f, deltaY = 48f) {
                it.node.dispatch(it)
            }

            val window = state.floatingWindows.single()
            assertEquals(151f, window.x)
            assertEquals(49f, window.y)
        }
    }

    @Test
    fun `undocked tab keeps dragging after frame refresh`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"))
        val input = HollowUiInputController()

        HollowComposeUiRuntime().use { runtime ->
            val firstFrame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            input.mouseClicked(firstFrame, mouseX = 150f, mouseY = 12f, button = 0, dispatch = { it.node.dispatch(it) }, openUrl = {})
            input.mouseDragged(firstFrame, mouseX = 160f, mouseY = 60f, button = 0, deltaX = 10f, deltaY = 48f) {
                it.node.dispatch(it)
            }

            val window = state.floatingWindows.single()
            assertEquals(window.id, state.draggedWindowId)
            assertEquals("dock-tab-console", input.draggingKey)

            val secondFrame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            input.mouseDragged(secondFrame, mouseX = 180f, mouseY = 80f, button = 0, deltaX = 20f, deltaY = 20f) {
                it.node.dispatch(it)
            }

            val moved = state.floatingWindows.single()
            assertEquals(window.x + 20f, moved.x)
            assertEquals(window.y + 20f, moved.y)
        }
    }

    @Test
    fun `dock space exposes move and resize cursors`() {
        val state = DockingState()
        state.openFloating(item("console"), x = 20f, y = 16f, width = 220f, height = 160f)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            val header = frame.resolved.styles.keys.single { it.id == "dock-window-1-header" }
            val rightResize = frame.resolved.styles.keys.single { it.id == "dock-window-1-resize-right" }
            val cornerResize = frame.resolved.styles.keys.single { it.id == "dock-window-1-resize-bottom_right" }

            assertEquals(UiCursorShape.MOVE, frame.resolved[header].cursor)
            assertEquals(UiCursorShape.RESIZE_HORIZONTAL, frame.resolved[rightResize].cursor)
            assertEquals(UiCursorShape.RESIZE_NWSE, frame.resolved[cornerResize].cursor)
        }
    }

    @Test
    fun `splitter is rendered and has resize cursor`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))
        val splitId = (state.root as DockNode.Split).id

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            val splitter = frame.resolved.styles.keys.single { it.id == "$splitId-splitter" }

            assertEquals(UiCursorShape.RESIZE_HORIZONTAL, frame.resolved[splitter].cursor)
            assertEquals(6f, frame.layout[splitter].rect.width)
        }
    }

    @Test
    fun `drop overlay is available when every window is floating`() {
        val state = DockingState()
        state.openFloating(item("console"), x = 20f, y = 16f)
        val window = state.floatingWindows.single()
        state.startDraggingWindow(window.id)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)

            assertNotNull(frame.resolved.styles.keys.firstOrNull { it.id == "dock-drop-root-center" })
        }
    }

    @Test
    fun `empty root drop overlay uses centered plus zones`() {
        val state = DockingState()
        state.openFloating(item("console"), x = 20f, y = 16f)
        state.startDraggingWindow(state.floatingWindows.single().id)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            val center = frame.layout[frame.resolved.styles.keys.first { it.id == "dock-drop-root-center" }].rect

            assertTrue(center.x > 250f)
            assertTrue(center.x + center.width < 390f)
        }
    }

    @Test
    fun `root edge drop zones stay above stack zones`() {
        val state = DockingState()
        state.open(item("files"))
        state.openFloating(item("console"), x = 20f, y = 16f)
        state.startDraggingWindow(state.floatingWindows.single().id)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            val hit = frame.hitTest(8f, 180f)

            assertEquals("dock-drop-root-left", hit?.node?.id)
        }
    }

    @Test
    fun `drop preview clears when leaving a zone`() {
        val state = DockingState()
        state.open(item("files"))
        state.openFloating(item("console"), x = 20f, y = 16f)
        val window = state.floatingWindows.single()
        state.startDraggingWindow(window.id)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(content = { DockSpace(state) { Text(it.title) } }, width = 640f, height = 360f)
            val zone = frame.resolved.styles.keys.first { it.id == "dock-drop-root-left" }

            zone.dispatch(UiEvent(UiEventKind.HOVER, zone))
            assertEquals(DockTarget(placement = DockPlacement.LEFT), state.previewTarget)

            zone.dispatch(UiEvent(UiEventKind.EXIT, zone))
            assertEquals(null, state.previewTarget)
        }
    }

    @Test
    fun `drop resolver selects side and center targets`() {
        val state = DockingState()
        state.open(item("files"))
        state.open(item("console"), DockTarget(placement = DockPlacement.RIGHT))

        val layouts = DockLayoutCalculator.layout(state.root, DockRect(0f, 0f, 400f, 200f))
        val leftStack = layouts.first { it.stack && it.rect.x == 0f }

        assertEquals(DockPlacement.LEFT, DockDropResolver.resolve(layouts, 8f, 100f)?.placement)
        assertEquals(DockPlacement.CENTER, DockDropResolver.resolve(layouts, leftStack.rect.x + 100f, 100f)?.placement)
        assertEquals(leftStack.nodeId, DockDropResolver.resolve(layouts, leftStack.rect.x + 100f, 100f)?.anchorId)
    }

    @Test
    fun `dock space composes root stack and floating windows`() {
        val state = DockingState()
        state.open(item("files"))
        state.openFloating(item("console"), x = 20f, y = 16f)

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    DockSpace(state) { item ->
                        Box(id = "content-${item.id}") {
                            Text(item.title)
                        }
                    }
                },
                width = 640f,
                height = 360f,
            )

            assertNotNull(frame.resolved.styles.keys.firstOrNull { it.id == "content-files" })
            assertNotNull(frame.resolved.styles.keys.firstOrNull { it.id == "content-console" })
        }
    }

    private fun stackBounds(state: DockingState): Map<String, DockRect> {
        return DockLayoutCalculator.layout(state.root, DockRect(0f, 0f, 100f, 100f))
            .filter { it.stack }
            .associate { it.nodeId to it.rect }
    }

    private fun item(id: String) = DockItem(id = id, title = id.replaceFirstChar { it.uppercase() })
}
