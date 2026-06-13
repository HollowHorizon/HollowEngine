package ru.hollowhorizon.hollowengine.client.ui.scripting

import androidx.compose.runtime.Composable
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowComposeUiScreen
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariUiEventPacket
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.utils.openUrl


@Serializable
enum class KatariUiDisplayMode {
    SCREEN,
    OVERLAY,
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ShowKatariUiPacket(
    private val id: String,
    private val root: UiXmlTree,
    private val mode: KatariUiDisplayMode,
    private val variables: @Serializable(ForCompoundNBT::class) CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            when (mode) {
                KatariUiDisplayMode.SCREEN -> Minecraft.getInstance().setScreen(KatariUiScreen(id, root, variables))
                KatariUiDisplayMode.OVERLAY -> KatariUiOverlays.show(id, root, variables)
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CloseKatariUiOverlayPacket(
    private val id: String,
    private val root: UiXmlTree,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            KatariUiOverlays.close(id, root)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class UpdateKatariUiOverlayPacket(
    private val id: String,
    private val root: UiXmlTree,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            KatariUiOverlays.update(id, root)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CloseKatariUiScreenPacket(private val id: String) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            val current = Minecraft.getInstance().screen
            if (current is KatariUiScreen && current.id == id) {
                current.startClosingAnimation()
            }
        }
    }
}

class KatariUiScreen(
    val id: String,
    private val root: UiXmlTree,
    private val variables: CompoundTag = CompoundTag(),
) : HollowComposeUiScreen("Katari UI", CompiledHss(emptyList())) {
    private val sink = UiEventSink { payload ->
        HollowEngine.LOGGER.info("[UI:$id]:\n ${payload.toPrettyString()}")
        KatariUiEventPacket(id, payload).send()
    }

    @Composable
    override fun Content() {
        UiXmlContent(root, UiXmlOptions(eventSink = sink))
    }

    override fun bindings(): UiBindingContext = UiBindingContext(variables)

    override fun eventSink(): UiEventSink = sink
}

private fun CompoundTag.toPrettyString(): String {
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    val jsonElement: JsonElement = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, this)
    return gson.toJson(jsonElement)
}

@ClientOnly
object KatariUiOverlays {
    private val overlays = linkedMapOf<String, KatariUiOverlay>()

    fun show(id: String, root: UiXmlTree, variables: CompoundTag = CompoundTag()) {
        overlays[id]?.show(root, variables) ?: run {
            overlays[id] = KatariUiOverlay(id, root, variables)
        }
    }

    fun update(id: String, root: UiXmlTree) {
        overlays[id]?.update(root)
    }

    fun close(id: String, root: UiXmlTree) {
        overlays[id]?.close(root)
    }

    fun closeAll() {
        overlays.forEach { (id, overlay) ->
            overlay.dispose()
        }
        overlays.clear()
    }

    fun handleMouseMove(x: Float, y: Float): Boolean {
        val point = overlayPoint(x, y)
        return overlays.values.toList().asReversed().any { it.mouseMoved(point.x, point.y) }
    }

    fun handleMouseButton(x: Float, y: Float, button: Int, action: Int, modifiers: Int): Boolean {
        val point = overlayPoint(x, y)
        return overlays.values.toList().asReversed().any { it.mouseButton(point.x, point.y, button, action, modifiers) }
    }

    fun handleMouseScroll(x: Float, y: Float, scrollX: Double, scrollY: Double): Boolean {
        val point = overlayPoint(x, y)
        return overlays.values.toList().asReversed().any { it.mouseScrolled(point.x, point.y, scrollX, scrollY) }
    }

    fun handleKey(key: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        return overlays.values.toList().asReversed().any { it.keyPressed(key, scanCode, action, modifiers) }
    }

    fun handleChar(codePoint: Int, modifiers: Int): Boolean {
        return overlays.values.toList().asReversed().any { it.charTyped(codePoint.toChar(), modifiers) }
    }

    fun hasFocusedInput(): Boolean {
        return overlays.values.any { it.hasFocusedInput() }
    }

    @SubscribeEvent
    fun render(event: RenderOverlayEvent.Post) {
        if (event.overlay != GuiOverlay.CHAT_PANEL) return
        val nowMillis = System.currentTimeMillis()
        overlays.entries.toList().forEach { (id, overlay) ->
            if (overlay.render(nowMillis)) {
                overlays.remove(id)?.dispose()
            }
        }
    }

    private fun overlayPoint(x: Float, y: Float): UiOverlayPoint {
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val sourceWidth = minecraft.mainRenderTarget.width.takeIf { it > 0 }?.toFloat() ?: window.width.toFloat()
        val sourceHeight = minecraft.mainRenderTarget.height.takeIf { it > 0 }?.toFloat() ?: window.height.toFloat()
        return UiOverlayPoint(
            x = x * window.guiScaledWidth.toFloat() / sourceWidth,
            y = y * window.guiScaledHeight.toFloat() / sourceHeight,
        )
    }
}

private class KatariUiOverlay(
    private val id: String,
    private var root: UiXmlTree,
    private var variables: CompoundTag,
) {
    private val surface = HollowUiSurface()
    private val renderer = MinecraftUiRenderer()
    private val input = HollowUiInputController()
    private val sink = UiEventSink { payload -> KatariUiEventPacket(id, payload).send() }
    private var preparedScripts: UiPreparedClientScripts = UiPreparedClientScripts.Empty
    private var cachedScriptHash: Int = 0
    private var node = composeNode()
    private var closing = false
    private var closingStartedAt: Long? = null
    private var closingDurationMillis: Long? = null
    private var closeBaseFrame: HollowUiFrame? = null
    private var lastFrame: HollowUiFrame? = null
    private var activeButton: Int? = null
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    fun show(root: UiXmlTree, variables: CompoundTag) {
        this.root = root
        this.variables = variables
        node = composeNode()
        closing = false
        closingStartedAt = null
        closingDurationMillis = null
        closeBaseFrame = null
        input.reset()
        activeButton = null
    }

    fun update(root: UiXmlTree) {
        this.root = root
        node = composeNode()
    }

    fun close(root: UiXmlTree) {
        this.root = root
        node = composeNode()
        closing = false
        closingStartedAt = null
        closingDurationMillis = null
        activeButton = null
        input.clearInteraction()
        closeBaseFrame = refreshFrame()
        closing = true
    }

    fun render(nowMillis: Long): Boolean {
        val frame = refreshFrame(nowMillis)
        if (!closing) {
            renderer.render(frame.commands)
            return false
        }
        val closeStartedAt = closingStartedAt ?: nowMillis.also { closingStartedAt = it }
        val duration = closingDurationMillis ?: frame.motionDurationMillis(closeBaseFrame).also {
            closingDurationMillis = it
        }
        if (duration <= 0L || nowMillis - closeStartedAt >= duration) return true
        renderer.render(frame.commands)
        return false
    }

    fun mouseMoved(mouseX: Float, mouseY: Float): Boolean {
        if (closing) return false
        val frame = lastFrame ?: return false
        val button = activeButton
        if (button == null) {
            val changed = input.updateHover(frame, mouseX, mouseY, ::dispatchUiEvent)
            if (changed) refreshFrame()
            return false
        }

        val deltaX = mouseX - lastMouseX
        val deltaY = mouseY - lastMouseY
        lastMouseX = mouseX
        lastMouseY = mouseY

        val scrollbarResult = input.scrollbarMouseDragged(frame, mouseX, mouseY, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            refreshFrame()
            return true
        }

        val result = input.mouseDragged(frame, mouseX, mouseY, button, deltaX, deltaY, ::dispatchUiEvent)
        if (result.handled) {
            refreshFrame()
            return true
        }
        return input.hasScrollbarDrag()
    }

    fun mouseButton(mouseX: Float, mouseY: Float, button: Int, action: Int, modifiers: Int): Boolean {
        if (closing) return false
        val frame = lastFrame ?: return false
        return when (action) {
            GLFW.GLFW_PRESS -> mousePressed(frame, mouseX, mouseY, button)
            GLFW.GLFW_RELEASE -> mouseReleased(frame, mouseX, mouseY, button)
            else -> false
        }
    }

    fun mouseScrolled(mouseX: Float, mouseY: Float, scrollX: Double, scrollY: Double): Boolean {
        if (closing) return false
        val frame = lastFrame ?: return false
        val target = input.scrollTargetAt(frame, mouseX, mouseY) ?: return false
        val range = frame.layout[target].scrollRange
        val delta = scrollWheelDelta(range, scrollX, scrollY, horizontalScrollModifierDown())
        val event = UiEvent(
            kind = UiEventKind.SCROLL,
            node = target,
            x = mouseX,
            y = mouseY,
            scrollX = delta.x,
            scrollY = delta.y,
        )
        if (dispatchUiEvent(event) && event.consumed) {
            refreshFrame()
            return true
        }
        surface.scroll(target, delta.x * 32f, delta.y * 32f)
        refreshFrame()
        return true
    }

    fun keyPressed(keyCode: Int, scanCode: Int, action: Int, modifiers: Int): Boolean {
        if (closing) return false
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return hasFocusedInput()
        val frame = lastFrame ?: return false
        val result = input.keyPressed(frame, keyCode, scanCode, modifiers, ::dispatchUiEvent)
        if (result.handled) refreshFrame()
        return result.handled
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (closing) return false
        val frame = lastFrame ?: return false
        val result = input.charTyped(frame, codePoint, modifiers, ::dispatchUiEvent)
        if (result.handled) refreshFrame()
        return result.handled
    }

    fun hasFocusedInput(): Boolean = input.focusedKey != null

    fun dispose() {
        surface.close()
        renderer.close()
    }

    private fun mousePressed(frame: HollowUiFrame, mouseX: Float, mouseY: Float, button: Int): Boolean {
        activeButton = button
        lastMouseX = mouseX
        lastMouseY = mouseY

        val scrollbarResult = input.scrollbarMouseClicked(frame, mouseX, mouseY, button, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            refreshFrame()
            return true
        }

        val result = input.mouseClicked(frame, mouseX, mouseY, button, ::dispatchUiEvent, ::openUrl)
        if (result.handled) {
            refreshFrame()
            return true
        }

        activeButton = null
        return false
    }

    private fun mouseReleased(frame: HollowUiFrame, mouseX: Float, mouseY: Float, button: Int): Boolean {
        val hadActivePointer = activeButton != null || input.hasScrollbarDrag()
        val result = input.mouseReleased(frame, mouseX, mouseY, button, ::dispatchUiEvent)
        activeButton = null
        if (result.handled || hadActivePointer) refreshFrame()
        return result.handled || hadActivePointer
    }

    private fun setScrollImmediate(node: UiNode, offset: UiScrollOffset) {
        surface.setScrollImmediate(node, offset.x, offset.y)
    }

    private fun refreshFrame(nowMillis: Long = System.currentTimeMillis()): HollowUiFrame {
        val window = Minecraft.getInstance().window
        return surface.frame(
            window.guiScaledWidth.toFloat(),
            window.guiScaledHeight.toFloat(),
            UiBindingContext(variables).withPointer(lastMouseX, lastMouseY),
            nowMillis,
            prepareRoot = { root ->
                node = root
                prepareClientScripts(root)
                input.prepareRoot(root, closing)
            },
        ).also { lastFrame = it }
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        val rootNode = lastFrame?.resolved?.root ?: node
        event.variables = variables
        var handled = false
        if (preparedScripts.dispatch(event, rootNode, variables)) handled = true
        if (!event.consumed && event.node.dispatch(event)) handled = true
        return handled
    }

    private fun horizontalScrollModifierDown(): Boolean {
        val window = Minecraft.getInstance().window.window
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
    }

    private fun composeNode(): BoxNode {
        return surface.setContent {
            UiXmlContent(root, UiXmlOptions(eventSink = sink))
        }.also(::prepareClientScripts)
    }

    private fun prepareClientScripts(root: UiNode) {
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
        preparedScripts = UiClientScriptRunner.prepare(scripts, root, sink, variables)
    }

}

private data class UiOverlayPoint(val x: Float, val y: Float)
