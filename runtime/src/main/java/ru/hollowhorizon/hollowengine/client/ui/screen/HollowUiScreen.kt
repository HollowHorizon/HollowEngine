package ru.hollowhorizon.hollowengine.client.ui.screen

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.HollowUiFrame
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiNodeKeys
import ru.hollowhorizon.hollowengine.client.ui.UiRect
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.common.utils.literal

abstract class HollowUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : Screen(title.literal) {
    private val runtime = HollowUiRuntime(stylesheet = stylesheet)
    private val renderer = MinecraftUiRenderer()
    private var frame: HollowUiFrame? = null
    private var cachedRoot: UiNode? = null
    private var uiDirty = true
    private var lastWidth = -1
    private var lastHeight = -1
    private var hoveredKey: String? = null
    private var activeKey: String? = null
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
        updateHover(mouseX.toFloat(), mouseY.toFloat())
        val nextFrame = refreshFrame()
        renderer.render(nextFrame.commands)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        updateHover(mouseX.toFloat(), mouseY.toFloat())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val scrollbar = findScrollbar(mouseX.toFloat(), mouseY.toFloat())
        if (button == 0 && scrollbar != null) {
            scrollbarDrag = scrollbar.toDrag(mouseX.toFloat(), mouseY.toFloat(), frame ?: return false)
            updateScrollbarDrag(mouseX.toFloat(), mouseY.toFloat())
            return true
        }
        val hit = frame?.hitTest(mouseX.toFloat(), mouseY.toFloat()) ?: return super.mouseClicked(mouseX, mouseY, button)
        activeKey = UiNodeKeys.key(hit.node)
        if (frame?.resolved?.get(hit.node)?.input?.draggable == true && button == 0) {
            draggingNodeKey = activeKey
            lastDragX = mouseX
            lastDragY = mouseY
            hit.node.states += UiState.DRAGGING
            invalidateUi(immediate = true)
            return true
        }
        if (onNodeClicked(hit.node, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
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
            val horizontal = if (hasShiftDown() && scrollX == 0.0) -scrollY else -scrollX
            val vertical = if (hasShiftDown()) 0.0 else -scrollY
            runtime.scroll(target, (horizontal * 32.0).toFloat(), (vertical * 32.0).toFloat())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun updateHover(mouseX: Float, mouseY: Float) {
        hoveredKey = frame?.hitTest(mouseX, mouseY)?.node?.let(UiNodeKeys::key)
    }

    protected fun isHovered(id: String): Boolean = hoveredKey == id

    private fun applyRuntimeStates(node: UiNode) {
        val key = UiNodeKeys.key(node)
        node.states -= UiState.HOVER
        node.states -= UiState.ACTIVE
        node.states -= UiState.DRAGGING
        if (key == hoveredKey) node.states += UiState.HOVER
        if (key == activeKey) node.states += UiState.ACTIVE
        if (key == draggingNodeKey) node.states += UiState.DRAGGING
        node.children.forEach(::applyRuntimeStates)
    }

    private fun currentRoot(): UiNode {
        val sizeChanged = width != lastWidth || height != lastHeight
        if (cachedRoot == null || uiDirty || sizeChanged || rebuildEveryFrame()) {
            cachedRoot = buildUi()
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

    override fun removed() {
        renderer.close()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false
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
