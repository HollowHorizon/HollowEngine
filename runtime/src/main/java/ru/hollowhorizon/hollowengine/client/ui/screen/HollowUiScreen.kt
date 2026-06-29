package ru.hollowhorizon.hollowengine.client.ui.screen

import kotlinx.coroutines.Job
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.openUrl
import java.util.*

private const val NanosPerMillisecond = 1_000_000L

abstract class HollowUiScreen(
    title: String,
    stylesheet: CompiledHss,
) : Screen(title.literal) {
    private val surface = HollowUiSurface(stylesheet = stylesheet)
    private val renderer = MinecraftUiRenderer()
    private var frame: HollowUiFrame? = null
    private var prepareScriptsJob: Job? = null
    private var uiDirty = true
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastFrameMouseX = Float.NaN
    private var lastFrameMouseY = Float.NaN
    private var lastStylesheetRevision = 0L
    private val input = HollowUiInputController()
    private var lastDragX = 0.0
    private var lastDragY = 0.0
    private var closing = false
    private var closeCompleted = false
    private var closeStartedAt = 0L
    private var closeBaseFrame: HollowUiFrame? = null
    private var closeDurationMillis: Long? = null
    protected var mouseX: Float = 0f
        private set
    protected var mouseY: Float = 0f
        private set

    protected abstract fun buildUi(): UiNode

    protected open fun eventSink(): UiEventSink = UiEventSink.None

    protected open fun onNodeClicked(node: UiNode, button: Int): Boolean = false

    protected open fun onNodeDragged(nodeKey: String, deltaX: Float, deltaY: Float): Boolean = false

    protected open fun onNodeDragged(node: UiNode, deltaX: Float, deltaY: Float): Boolean = false

    protected open fun rebuildEveryFrame(): Boolean = false

    protected open fun applyPendingUiChanges(nowNanos: Long = System.nanoTime()): Boolean = false

    protected fun installComposeContent(content: HollowUiContent): BoxNode {
        return surface.setContent(content)
    }

    protected fun composeRoot(nowNanos: Long = System.nanoTime()): BoxNode {
        return surface.composeRoot(nowNanos)
    }

    protected fun applyComposePendingChanges(nowNanos: Long = System.nanoTime()): Boolean {
        return surface.applyPendingChanges(nowNanos)
    }

    protected fun invalidateUi(immediate: Boolean = false) {
        uiDirty = true
        if (immediate && frame == null && width > 0 && height > 0) refreshFrame()
    }

    protected fun layoutRect(id: String): UiRect? {
        return frame?.layout?.nodes?.entries
            ?.firstOrNull { it.key.id == id }
            ?.value
            ?.rect
    }

    fun startClosingAnimation() {
        if (closing || closeCompleted) return
        val nowMillis = System.currentTimeMillis()
        input.clearInteraction()
        closeBaseFrame = if (width > 0 && height > 0) {
            refreshFrame(nowMillis)
        } else {
            frame
        }
        closing = true
        closeStartedAt = nowMillis
        closeDurationMillis = null
        val current = if (width > 0 && height > 0) refreshFrame(nowMillis) else null
        val duration = current?.motionDurationMillis(closeBaseFrame) ?: 0L
        closeDurationMillis = duration
        if (duration <= 0L) {
            closeCompleted = true
            Minecraft.getInstance().setScreen(null)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val nowMillis = System.currentTimeMillis()
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        refreshFrame(System.currentTimeMillis())
        val current = frame ?: return
        if (completeClosingIfReady(current, nowMillis)) return
        val activeFrame = if (closing) {
            current
        } else {
            val hadHoverChange = input.updateHover(current, mouseX.toFloat(), mouseY.toFloat(), ::dispatchUiEvent)
            applyCursor(current)
            if (hadHoverChange || current.requiresContinuousRefresh()) uiDirty = true
            current
        }
        if (!closing) {
            input.dispatchHover(activeFrame, mouseX.toFloat(), mouseY.toFloat(), ::dispatchUiEvent)
        }
        renderer.render(activeFrame.commands)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (closing) return
        this.mouseX = mouseX.toFloat()
        this.mouseY = mouseY.toFloat()
        val current = frame ?: return
        val hoverChanged = input.updateHover(current, mouseX.toFloat(), mouseY.toFloat(), ::dispatchUiEvent)
        applyCursor(current)
        if (hoverChanged) {
            invalidateUi()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closing) return true
        val current = frame ?: return super.mouseClicked(mouseX, mouseY, button)
        val scrollbarResult = input.scrollbarMouseClicked(current, mouseX.toFloat(), mouseY.toFloat(), button, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            invalidateUi()
            return true
        }
        val result = input.mouseClicked(current, mouseX.toFloat(), mouseY.toFloat(), button, ::dispatchUiEvent, ::openUrl)
        if (result.handled) {
            lastDragX = mouseX
            lastDragY = mouseY
            applyCursor(current)
            invalidateUi()
            return true
        }
        if (result.node != null && onNodeClicked(result.node, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closing) return true
        frame?.let { current ->
            input.mouseReleased(current, mouseX.toFloat(), mouseY.toFloat(), button, ::dispatchUiEvent)
        }
        invalidateUi()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (closing) return true
        frame?.let { current ->
            val scrollbarResult = input.scrollbarMouseDragged(current, mouseX.toFloat(), mouseY.toFloat(), ::setScrollImmediate)
            if (scrollbarResult.handled) {
                invalidateUi()
                return true
            }
        }
        val key = input.draggingKey ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val dx = (mouseX - lastDragX).toFloat()
        val dy = (mouseY - lastDragY).toFloat()
        lastDragX = mouseX
        lastDragY = mouseY
        if (onNodeDragged(key, dx, dy)) {
            frame?.let(::applyCursor)
            invalidateUi()
            return true
        }
        val current = frame ?: return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        val result = input.mouseDragged(current, mouseX.toFloat(), mouseY.toFloat(), button, dx, dy, ::dispatchUiEvent)
        if (result.handled) {
            applyCursor(current)
            invalidateUi()
            return true
        }
        if (result.node != null && onNodeDragged(result.node, dx, dy)) {
            applyCursor(current)
            invalidateUi()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (closing) return true
        val current = frame ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val target = input.scrollTargetAt(current, mouseX.toFloat(), mouseY.toFloat())
        if (target != null) {
            val range = current.layout[target].scrollRange
            val delta = scrollWheelDelta(range, scrollX, scrollY, hasShiftDown() || hasControlDown())
            val event = UiEvent(
                kind = UiEventKind.SCROLL,
                node = target,
                x = mouseX.toFloat(),
                y = mouseY.toFloat(),
                scrollX = delta.x,
                scrollY = delta.y,
            )
            if (dispatchUiEvent(event) && event.consumed) {
                invalidateUi()
                return true
            }
            surface.scroll(target, delta.x * 32f, delta.y * 32f)
            invalidateUi()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (closing) return false
        val current = frame ?: return super.charTyped(codePoint, modifiers)
        val result = input.charTyped(current, codePoint, modifiers, ::dispatchUiEvent)
        if (result.handled) {
            invalidateUi()
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (closing) return false
        val current = frame ?: return super.keyPressed(keyCode, scanCode, modifiers)
        val result = input.keyPressed(current, keyCode, scanCode, modifiers, ::dispatchUiEvent)
        if (result.handled) {
            invalidateUi()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    protected fun isHovered(id: String): Boolean {
        if (input.isHovered(id)) return true
        val current = frame ?: return false
        val node = current.resolved.styles.keys.firstOrNull { it.id == id } ?: return false
        val key = input.hoveredKey ?: return false
        return UiNodeKeys.key(node) == key || node.hasDescendantKey(key)
    }

    private fun setScrollImmediate(node: UiNode, offset: UiScrollOffset) {
        surface.setScrollImmediate(node, offset.x, offset.y)
    }

    private fun refreshFrame(nowMillis: Long = System.currentTimeMillis()): HollowUiFrame {
        buildUi()
        val root: BoxNode
        val nextFrame = surface.frame(width.toFloat(), height.toFloat(), nowMillis) {
            input.prepareRoot(it, closing)
            root = it
        }
        frame = nextFrame
        lastFrameMouseX = mouseX
        lastFrameMouseY = mouseY
        lastStylesheetRevision = root.stylesheetRevision()
        lastWidth = width
        lastHeight = height
        return nextFrame
    }

    override fun removed() {
        UiCursorApplier.apply(UiCursorShape.DEFAULT)
        frame?.let { current ->
            dispatchUiEvent(UiEvent(UiEventKind.CLOSE, current.resolved.root))
        }
        surface.close()
        renderer.close()
        prepareScriptsJob?.cancel()
        super.removed()
    }

    private fun applyCursor(current: HollowUiFrame) {
        val node = input.draggingKey?.let(current::nodeByKey)
            ?: input.hoveredKey?.let(current::nodeByKey)
        val shape = node?.let { current.resolved[it].cursor } ?: UiCursorShape.DEFAULT
        UiCursorApplier.apply(shape)
    }

    override fun onClose() {
        if (closeCompleted) {
            super.onClose()
        } else {
            startClosingAnimation()
        }
    }

    private fun completeClosingIfReady(current: HollowUiFrame, nowMillis: Long): Boolean {
        if (!closing) return false
        val duration = closeDurationMillis ?: current.motionDurationMillis(closeBaseFrame).also {
            closeDurationMillis = it
        }
        if (nowMillis - closeStartedAt < duration) return false
        closeCompleted = true
        Minecraft.getInstance().setScreen(null)
        return true
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        var handled = false
        if (!event.consumed && event.node.dispatch(event)) handled = true
        return handled
    }

    override fun isPauseScreen(): Boolean = false
}

private fun UiNode.hasLiveCursorPopup(): Boolean {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        for (child in node.children) {
            if (child is PopupNode && child.anchor.isLiveCursor()) return true
            stack.add(child)
        }
    }
    return false
}

private fun UiNode.hasDescendantKey(key: String): Boolean {
    val stack = ArrayDeque<UiNode>()
    children.asReversed().forEach(stack::add)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (UiNodeKeys.key(node) == key) return true
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
    return false
}

private fun UiPopupAnchor.isLiveCursor(): Boolean {
    return this is UiPopupAnchor.Cursor && (!x.isFinite() || !y.isFinite())
}

private object UiCursorApplier {
    private var current = UiCursorShape.DEFAULT
    private val cursors = mutableMapOf<UiCursorShape, Long>()

    fun apply(shape: UiCursorShape) {
        if (shape == current) return
        current = shape
        GLFW.glfwSetCursor(Minecraft.getInstance().window.window, cursor(shape))
    }

    private fun cursor(shape: UiCursorShape): Long {
        if (shape == UiCursorShape.DEFAULT) return 0L
        return cursors.getOrPut(shape) {
            GLFW.glfwCreateStandardCursor(shape.glfw)
        }
    }

    private val UiCursorShape.glfw: Int
        get() = when (this) {
            UiCursorShape.DEFAULT -> GLFW.GLFW_ARROW_CURSOR
            UiCursorShape.HAND -> GLFW.GLFW_HAND_CURSOR
            UiCursorShape.MOVE -> GLFW.GLFW_RESIZE_ALL_CURSOR
            UiCursorShape.TEXT -> GLFW.GLFW_IBEAM_CURSOR
            UiCursorShape.RESIZE_HORIZONTAL -> GLFW.GLFW_RESIZE_EW_CURSOR
            UiCursorShape.RESIZE_VERTICAL -> GLFW.GLFW_RESIZE_NS_CURSOR
            UiCursorShape.RESIZE_NESW -> GLFW.GLFW_RESIZE_NESW_CURSOR
            UiCursorShape.RESIZE_NWSE -> GLFW.GLFW_RESIZE_NWSE_CURSOR
        }
}
