package ru.hollowhorizon.hollowengine.client.ui.screen

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUiFrame
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.ScriptEventModifier
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.UiClientScriptModifier
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiNodeKeys
import ru.hollowhorizon.hollowengine.client.ui.UiRect
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.dispatch
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiPreparedClientScripts
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScript
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScriptRunner
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.openUrl

abstract class HollowUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : Screen(title.literal) {
    private val runtime = HollowUiRuntime(stylesheet = stylesheet)
    private val renderer = MinecraftUiRenderer()
    private var frame: HollowUiFrame? = null
    private var cachedRoot: UiNode? = null
    private var preparedScripts: UiPreparedClientScripts = UiPreparedClientScripts.Empty
    private var prepareScriptsJob: Job? = null
    private var cachedScriptHash: Int = 0
    private var uiDirty = true
    private var lastWidth = -1
    private var lastHeight = -1
    private var hoveredKey: String? = null
    private var hoveredLink: String? = null
    private var activeKey: String? = null
    private var focusedKey: String? = null
    private var draggingNodeKey: String? = null
    private var scrollbarDrag: ScrollbarDrag? = null
    private var lastDragX = 0.0
    private var lastDragY = 0.0
    protected var mouseX: Float = 0f
        private set
    protected var mouseY: Float = 0f
        private set

    protected abstract fun buildUi(): UiNode

    protected open fun bindings(): UiBindingContext = UiBindingContext()

    protected open fun eventSink(): UiEventSink = UiEventSink.None

    protected open fun onNodeClicked(node: UiNode, button: Int): Boolean = false

    protected open fun onNodeDragged(nodeKey: String, deltaX: Float, deltaY: Float): Boolean = false

    protected open fun onNodeDragged(node: UiNode, deltaX: Float, deltaY: Float): Boolean = false

    protected open fun rebuildEveryFrame(): Boolean = false

    protected fun invalidateUi(immediate: Boolean = false) {
        uiDirty = true
        if (immediate && width > 0 && height > 0) refreshFrame()
    }

    protected fun layoutRect(id: String): UiRect? {
        return frame?.layout?.nodes?.entries
            ?.firstOrNull { it.key.id == id }
            ?.value
            ?.rect
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        var nextFrame = refreshFrame()
        if (updateHover(nextFrame, mouseX.toFloat(), mouseY.toFloat())) {
            nextFrame = refreshFrame()
        }
        hoveredKey?.let { key ->
            nextFrame.nodeByKey(key)?.let { node ->
                dispatchUiEvent(
                    UiEvent(
                        kind = UiEventKind.HOVER,
                        node = node,
                        x = mouseX.toFloat(),
                        y = mouseY.toFloat(),
                    )
                )
            }
        }
        renderer.render(nextFrame.commands)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        val current = currentFrameForInput() ?: return
        if (updateHover(current, mouseX.toFloat(), mouseY.toFloat())) {
            refreshFrame()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val scrollbar = findScrollbar(mouseX.toFloat(), mouseY.toFloat())
        if (button == 0 && scrollbar != null) {
            scrollbarDrag = scrollbar.toDrag(mouseX.toFloat(), mouseY.toFloat(), frame ?: return false)
            updateScrollbarDrag(mouseX.toFloat(), mouseY.toFloat())
            return true
        }
        val hit = frame?.hitTest(mouseX.toFloat(), mouseY.toFloat()) ?: return super.mouseClicked(mouseX, mouseY, button)
        if (button == 0 && hit.link != null) {
            openUrl(hit.link)
            return true
        }
        activeKey = UiNodeKeys.key(hit.node)
        updateFocus(hit.node)
        val press = UiEvent(
            kind = UiEventKind.PRESS,
            node = hit.node,
            button = button,
            x = mouseX.toFloat(),
            y = mouseY.toFloat(),
            localX = hit.localX,
            localY = hit.localY,
        )
        if (dispatchUiEvent(press) && press.consumed) {
            invalidateUi(immediate = true)
            return true
        }
        if (frame?.resolved?.get(hit.node)?.input?.draggable == true && button == 0) {
            draggingNodeKey = activeKey
            lastDragX = mouseX
            lastDragY = mouseY
            hit.node.states += UiState.DRAGGING
            invalidateUi(immediate = true)
            return true
        }
        if (dispatchUiEvent(
                UiEvent(
                    kind = UiEventKind.CLICK,
                    node = hit.node,
                    button = button,
                    x = mouseX.toFloat(),
                    y = mouseY.toFloat(),
                    localX = hit.localX,
                    localY = hit.localY,
                )
            )
        ) {
            invalidateUi(immediate = true)
            return true
        }
        if (onNodeClicked(hit.node, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val current = frame
        val releaseNode = current?.hitTest(mouseX.toFloat(), mouseY.toFloat())?.node
            ?: activeKey?.let { current?.nodeByKey(it) }
        releaseNode?.let { node ->
            dispatchUiEvent(
                UiEvent(
                    kind = UiEventKind.RELEASE,
                    node = node,
                    button = button,
                    x = mouseX.toFloat(),
                    y = mouseY.toFloat(),
                    released = true,
                )
            )
        }
        activeKey = null
        draggingNodeKey = null
        scrollbarDrag = null
        invalidateUi(immediate = true)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        scrollbarDrag?.let {
            updateScrollbarDrag(mouseX.toFloat(), mouseY.toFloat())
            return true
        }
        val key = draggingNodeKey ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val dx = (mouseX - lastDragX).toFloat()
        val dy = (mouseY - lastDragY).toFloat()
        lastDragX = mouseX
        lastDragY = mouseY
        if (onNodeDragged(key, dx, dy)) {
            invalidateUi(immediate = true)
            return true
        }
        val current = frame ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val node = current.resolved.styles.keys.firstOrNull { UiNodeKeys.key(it) == key }
            ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        if (dispatchUiEvent(
                UiEvent(
                    kind = UiEventKind.DRAG,
                    node = node,
                    button = button,
                    x = mouseX.toFloat(),
                    y = mouseY.toFloat(),
                    deltaX = dx,
                    deltaY = dy,
                )
            )
        ) {
            invalidateUi(immediate = true)
            return true
        }
        if (onNodeDragged(node, dx, dy)) {
            invalidateUi(immediate = true)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val current = frame ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val target = current.resolved.styles.entries
            .asSequence()
            .filter { it.value.input.scrollable }
            .map { it.key to current.layout[it.key] }
            .filter { (_, layout) -> layout.content.contains(mouseX.toFloat(), mouseY.toFloat()) }
            .maxByOrNull { (_, layout) -> layout.rect.x + layout.rect.y }
            ?.first
        if (target != null) {
            val emulateHorizontal = (hasShiftDown() || hasControlDown()) && scrollX == 0.0
            val horizontal = if (emulateHorizontal) -scrollY else -scrollX
            val vertical = if (hasShiftDown() || hasControlDown()) 0.0 else -scrollY
            val event = UiEvent(
                kind = UiEventKind.SCROLL,
                node = target,
                x = mouseX.toFloat(),
                y = mouseY.toFloat(),
                scrollX = horizontal.toFloat(),
                scrollY = vertical.toFloat(),
            )
            if (dispatchUiEvent(event) && event.consumed) {
                invalidateUi(immediate = true)
                return true
            }
            runtime.scroll(target, (horizontal * 32.0).toFloat(), (vertical * 32.0).toFloat())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val node = focusedKey?.let { frame?.nodeByKey(it) } ?: return super.charTyped(codePoint, modifiers)
        if (dispatchUiEvent(UiEvent(UiEventKind.CHAR_TYPED, node, modifiers = modifiers, codePoint = codePoint.code))) {
            invalidateUi(immediate = true)
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 258 && focusNext()) {
            invalidateUi(immediate = true)
            return true
        }
        val node = focusedKey?.let { frame?.nodeByKey(it) } ?: return super.keyPressed(keyCode, scanCode, modifiers)
        val event = UiEvent(UiEventKind.KEY_PRESSED, node, key = keyCode, scanCode = scanCode, modifiers = modifiers)
        if (dispatchUiEvent(event)) {
            invalidateUi(immediate = true)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun updateHover(currentFrame: HollowUiFrame, mouseX: Float, mouseY: Float): Boolean {
        val hit = currentFrame.hitTest(mouseX, mouseY)
        val previousKey = hoveredKey
        hoveredKey = hit?.node?.let(UiNodeKeys::key)
        hoveredLink = hit?.link
        if (previousKey != hoveredKey) {
            previousKey?.let { key ->
                currentFrame.nodeByKey(key)?.let { dispatchUiEvent(UiEvent(UiEventKind.EXIT, it, x = mouseX, y = mouseY)) }
            }
            hoveredKey?.let { key ->
                currentFrame.nodeByKey(key)?.let { dispatchUiEvent(UiEvent(UiEventKind.ENTER, it, x = mouseX, y = mouseY)) }
            }
            return true
        }
        return false
    }

    protected fun isHovered(id: String): Boolean = hoveredKey == id

    private fun applyRuntimeStates(node: UiNode) {
        val key = UiNodeKeys.key(node)
        node.states -= UiState.HOVER
        node.states -= UiState.ACTIVE
        node.states -= UiState.DRAGGING
        if (node is TextNode) {
            node.hoveredLink = if (key == hoveredKey) hoveredLink else null
        }
        if (key == hoveredKey || node.containsNodeKey(hoveredKey)) node.states += UiState.HOVER
        if (key == activeKey) node.states += UiState.ACTIVE
        if (key == focusedKey) node.states += UiState.FOCUS
        if (key == draggingNodeKey) node.states += UiState.DRAGGING
        node.children.forEach(::applyRuntimeStates)
    }

    private fun currentRoot(): UiNode {
        val sizeChanged = width != lastWidth || height != lastHeight
        if (cachedRoot == null || uiDirty || sizeChanged || rebuildEveryFrame()) {
            cachedRoot = buildUi()
            schedulePrepareClientScripts(cachedRoot!!)
            uiDirty = false
            lastWidth = width
            lastHeight = height
        }
        return cachedRoot!!
    }

    private fun findScrollbar(mouseX: Float, mouseY: Float): DrawScrollbarCommand? {
        return frame?.commands
            ?.filterIsInstance<DrawScrollbarCommand>()
            ?.lastOrNull { it.track.contains(mouseX, mouseY) }
    }

    private fun DrawScrollbarCommand.toDrag(mouseX: Float, mouseY: Float, frame: HollowUiFrame): ScrollbarDrag {
        val layout = frame.layout[node]
        val offset = layout.scrollOffset
        return ScrollbarDrag(
            nodeKey = UiNodeKeys.key(node),
            orientation = orientation,
            track = track,
            thumb = thumb,
            startMouseX = mouseX,
            startMouseY = mouseY,
            startOffsetX = offset.x,
            startOffsetY = offset.y,
        )
    }

    private fun updateScrollbarDrag(mouseX: Float, mouseY: Float) {
        val drag = scrollbarDrag ?: return
        val frame = frame ?: return
        val node = frame.resolved.styles.keys.firstOrNull { UiNodeKeys.key(it) == drag.nodeKey } ?: return
        val layout = frame.layout[node]
        when (drag.orientation) {
            ScrollbarOrientation.VERTICAL -> {
                val movable = (drag.track.height - drag.thumb.height).coerceAtLeast(1f)
                val delta = (mouseY - drag.startMouseY) / movable * layout.scrollRange.y
                runtime.setScrollImmediate(node, y = drag.startOffsetY + delta)
            }
            ScrollbarOrientation.HORIZONTAL -> {
                val movable = (drag.track.width - drag.thumb.width).coerceAtLeast(1f)
                val delta = (mouseX - drag.startMouseX) / movable * layout.scrollRange.x
                runtime.setScrollImmediate(node, x = drag.startOffsetX + delta)
            }
        }
        refreshFrame()
    }

    private fun refreshFrame(nowMillis: Long = System.currentTimeMillis()): HollowUiFrame {
        val root = currentRoot()
        UiNodeKeys.assign(root)
        applyRuntimeStates(root)
        val nextFrame = runtime.frame(root, width.toFloat(), height.toFloat(), bindings(), nowMillis)
        frame = nextFrame
        return nextFrame
    }

    private fun currentFrameForInput(): HollowUiFrame? {
        if (width <= 0 || height <= 0) return frame
        val sizeChanged = width != lastWidth || height != lastHeight
        return if (frame == null || uiDirty || sizeChanged || rebuildEveryFrame()) refreshFrame() else frame
    }

    override fun removed() {
        frame?.let { current ->
            dispatchUiEvent(UiEvent(UiEventKind.CLOSE, current.resolved.root))
        }
        renderer.close()
        prepareScriptsJob?.cancel()
        super.removed()
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        val root = frame?.resolved?.root ?: event.node
        val variables = bindings().root
        event.variables = variables
        var handled = false
        if (preparedScripts.dispatch(event, root, variables)) handled = true
        if (!event.consumed && event.node.dispatch(event)) handled = true
        return handled
    }

    private fun schedulePrepareClientScripts(root: UiNode) {
        UiNodeKeys.assign(root)
        val scripts = root.clientScripts()
        if (scripts.isEmpty()) {
            preparedScripts = UiPreparedClientScripts.Empty
            cachedScriptHash = 0
            return
        }
        val scriptsHash = scripts.hashCode()
        if (scriptsHash == cachedScriptHash) {
            preparedScripts.applyInputHints(root)
            return
        }
        cachedScriptHash = scriptsHash
        preparedScripts = UiPreparedClientScripts.Empty
        prepareScriptsJob?.cancel()
        val sink = eventSink()
        val variables = bindings().root
        val minecraft = Minecraft.getInstance()
        prepareScriptsJob = minecraft.coroutineScope.launch(Dispatchers.IO) {
            val prepared = UiClientScriptRunner.prepare(scripts, root, sink, variables, applyInputHints = false)
            minecraft.execute {
                if (cachedScriptHash == scriptsHash) {
                    val currentRoot = cachedRoot ?: return@execute
                    prepared.applyInputHints(currentRoot)
                    preparedScripts = prepared
                    if (width > 0 && height > 0) refreshFrame()
                }
            }
        }
    }

    private fun updateFocus(node: UiNode) {
        val current = frame ?: return
        if (current.resolved[node].input.focusable) {
            setFocus(UiNodeKeys.key(node))
        } else {
            setFocus(null)
        }
    }

    private fun setFocus(nextKey: String?) {
        val current = frame ?: return
        if (focusedKey == nextKey) return
        focusedKey?.let { key ->
            current.nodeByKey(key)?.let { dispatchUiEvent(UiEvent(UiEventKind.UNFOCUS, it)) }
        }
        focusedKey = nextKey
        focusedKey?.let { key ->
            current.nodeByKey(key)?.let { dispatchUiEvent(UiEvent(UiEventKind.FOCUS, it)) }
        }
    }

    private fun focusNext(): Boolean {
        val current = frame ?: return false
        val focusables = current.resolved.styles.keys.filter { current.resolved[it].input.focusable }
        if (focusables.isEmpty()) return false
        val currentIndex = focusables.indexOfFirst { UiNodeKeys.key(it) == focusedKey }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % focusables.size
        setFocus(UiNodeKeys.key(focusables[nextIndex]))
        return true
    }

    override fun isPauseScreen(): Boolean = false
}

private fun HollowUiFrame.nodeByKey(key: String): UiNode? {
    return resolved.styles.keys.firstOrNull { UiNodeKeys.key(it) == key }
}

private fun UiNode.clientScripts(): List<UiClientScript> {
    val ownScripts = modifiers
        .filterIsInstance<UiClientScriptModifier>()
        .flatMap { it.scripts } +
            modifiers
                .filterIsInstance<ScriptEventModifier>()
                .map { modifier ->
                    UiClientScript.Inline(
                        kind = modifier.kind,
                        source = modifier.source,
                        targetKey = UiNodeKeys.key(this),
                        sink = modifier.sink,
                    )
                }
    return ownScripts + children.flatMap { it.clientScripts() }
}

private fun UiNode.containsNodeKey(key: String?): Boolean {
    if (key == null) return false
    return children.any { UiNodeKeys.key(it) == key || it.containsNodeKey(key) }
}

private data class ScrollbarDrag(
    val nodeKey: String,
    val orientation: ScrollbarOrientation,
    val track: UiRect,
    val thumb: UiRect,
    val startMouseX: Float,
    val startMouseY: Float,
    val startOffsetX: Float,
    val startOffsetY: Float,
)
