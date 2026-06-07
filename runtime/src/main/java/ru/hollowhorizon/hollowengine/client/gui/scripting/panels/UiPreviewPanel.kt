package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import androidx.compose.runtime.mutableStateOf as composeStateOf
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logD
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.DrawContext
import ru.hollowhorizon.hollowengine.client.kool.GlCanvas
import ru.hollowhorizon.hollowengine.client.kool.KEY_CODE_MAP
import ru.hollowhorizon.hollowengine.client.ui.Box as ComposeBox
import ru.hollowhorizon.hollowengine.client.ui.HollowComposeUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.HollowUiFrame
import ru.hollowhorizon.hollowengine.client.ui.HollowUiInputController
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.HssResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiNode as HollowUiNode
import ru.hollowhorizon.hollowengine.client.ui.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.dispatch
import ru.hollowhorizon.hollowengine.client.ui.hasScrollableAxis
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.scrollWheelDelta
import ru.hollowhorizon.hollowengine.client.ui.xml.UiResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryWatcher
import ru.hollowhorizon.hollowengine.common.utils.openUrl
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.generated.Assets
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.util.*
import kotlin.math.roundToInt

object UiPreviewState {
    val previewPath = mutableStateOf<String?>(null)
    val errorText = mutableStateOf<String?>(null)
    val previewScale = mutableStateOf(1f)
}

class UiPreviewPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.ui_preview", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.CODE_EDITOR

    init {
        UiPreviewResourceHotReload.start()
    }

    override fun UiScope.compose() {
        val path = UiPreviewState.previewPath.use()
        val scale = UiPreviewState.previewScale.use()
        Box(Grow.Std, Grow.Std) {
            modifier.backgroundColor(Color("101216"))

            if (path == null) {
                Text("Open a .ui file and press preview") {
                    modifier
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .textColor(ColorTheme.UI.ForegroundSecondary)
                }
                return@Box
            }

            GlCanvas("Hollow UI Preview", glCanvas = { drawPreview(path, scale); surface.triggerUpdate() }) {
                modifier.size(Grow.Std, Grow.Std)
                modifier.onClick { UiPreviewRenderer.click(it.screenPosition.x, it.screenPosition.y) }
                modifier.onDragStart {
                    if (it.pointer.isLeftButtonDown) {
                        UiPreviewRenderer.press(it.screenPosition.x, it.screenPosition.y, 0)
                    }
                }
                modifier.onDrag {
                    if (it.pointer.isLeftButtonDown) {
                        UiPreviewRenderer.drag(
                            it.screenPosition.x,
                            it.screenPosition.y,
                            it.pointer.delta.x,
                            it.pointer.delta.y,
                            0,
                        )
                    }
                }
                modifier.onDragEnd {
                    if (it.pointer.isLeftButtonReleased) {
                        UiPreviewRenderer.release(it.screenPosition.x, it.screenPosition.y, 0)
                    }
                }
                modifier.onWheelX {
                    UiPreviewRenderer.scroll(
                        it.screenPosition.x,
                        it.screenPosition.y,
                        it.pointer.scroll.x,
                        0f
                    )
                }
                modifier.onWheelY {
                    UiPreviewRenderer.scroll(
                        it.screenPosition.x,
                        it.screenPosition.y,
                        0f,
                        it.pointer.scroll.y
                    )
                }
            }

            UiPreviewState.errorText.use()?.let { error ->
                Text(error) {
                    modifier
                        .align(AlignmentX.Start, AlignmentY.Bottom)
                        .margin(Dimensions.PaddingMedium)
                        .textColor(Color("ff7d7d"))
                        .zLayer(1000)
                }
            }
        }
    }

    override fun onKeyInput(event: KeyEvent) {
        if (UiPreviewRenderer.keyInput(event)) {
            event.isConsumed = true
        }
    }

    override fun UiScope.drawHeaderRight(color: Color) {
        val scale = UiPreviewState.previewScale.use()
        Text("Scale ${String.format(Locale.ROOT, "%.2f", scale)}x") {
            modifier
                .alignY(AlignmentY.Center)
                .margin(end = Dimensions.PaddingMedium)
                .textColor(ColorTheme.UI.WhiteReplacement)
        }

        Slider(value = scale, min = PreviewScaleMin, max = PreviewScaleMax) {
            modifier
                .width(Dp(128f))
                .alignY(AlignmentY.Center)
                .margin(end = Dimensions.PaddingMedium)
                .colors(
                    ColorTheme.UI.WhiteReplacement,
                    ColorTheme.UI.BackgroundAccent,
                    ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                )
            modifier.onChange(::setPreviewScale)
        }

        Button("Game") {
            modifier
                .alignY(AlignmentY.Center)
                .textColor(ColorTheme.UI.WhiteReplacement)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingSmall,
                        Dimensions.PaddingSmall
                    )
                )
            modifier.onClick { setPreviewScale(Minecraft.getInstance().window.guiScale.toFloat()) }
        }
    }
}

private object UiPreviewRenderer {
    private val runtime = HollowComposeUiRuntime()
    private val renderer = MinecraftUiRenderer()
    private val input = HollowUiInputController()
    private val contentTree = composeStateOf(UiXmlTree("box"))
    private val contentSize = composeStateOf(UiPreviewSize(1f, 1f))
    private var lastFrame: HollowUiFrame? = null
    private var lastContext: DrawContext? = null
    private var lastTarget: UiRenderTarget? = null
    private var lastPath: String? = null
    private var lastSource: String? = null
    private var contentInstalled = false

    fun render(path: String, context: DrawContext, scale: Float) {
        try {
            if (path != lastPath) {
                input.reset()
                lastPath = path
                lastSource = null
            }
            val target = context.toRenderTarget(scale)
            lastContext = context
            lastTarget = target
            val source = path.fromReadablePath().readText()
            if (source != lastSource) {
                contentTree.value = parseUiXml(source, path)
                lastSource = source
            }
            contentSize.value = UiPreviewSize(target.logicalWidth, target.logicalHeight)
            ensureContentInstalled()
            val root = runtime.root
            input.prepareRoot(root)
            val now = System.currentTimeMillis()
            var frame = runtime.frame(target.logicalWidth, target.logicalHeight, nowMillis = now)
            val localMouse = context.localMouse(target)
            if (localMouse != null && input.updateHover(frame, localMouse.x, localMouse.y, ::dispatchPreviewEvent)) {
                input.prepareRoot(root)
                frame = runtime.frame(target.logicalWidth, target.logicalHeight, nowMillis = now)
            }
            localMouse?.let { input.dispatchHover(frame, it.x, it.y, ::dispatchPreviewEvent) }
            lastFrame = frame
            PreviewCheckerboard.draw(context)
            renderer.render(frame.commands, target)
            UiPreviewState.errorText.set(null)
        } catch (exception: Exception) {
            UiPreviewState.errorText.set(exception.message ?: exception::class.simpleName ?: "Preview error")
        }
    }

    fun scroll(mouseX: Float, mouseY: Float, scrollX: Float, scrollY: Float) {
        val frame = lastFrame ?: return
        val local = localPosition(mouseX, mouseY) ?: return
        val node = frame.scrollTargetAt(local.x, local.y)
            ?: input.focusedKey
                ?.let(frame::nodeByKey)
                ?.takeIf { frame.resolved[it].input.scrollable && frame.layout[it].scrollRange.hasScrollableAxis() }
            ?: return
        val delta = scrollWheelDelta(frame.layout[node].scrollRange, scrollX.toDouble(), scrollY.toDouble(), horizontalScrollModifierDown())
        runtime.scroll(node, delta.x * 32f, delta.y * 32f)
    }

    fun click(mouseX: Float, mouseY: Float) {
        val frame = lastFrame ?: return
        val local = localPosition(mouseX, mouseY) ?: return
        val scrollbarResult = input.scrollbarMouseClicked(frame, local.x, local.y, 0, ::setScrollImmediate)
        if (scrollbarResult.handled) {
            input.mouseReleased(frame, local.x, local.y, 0, ::dispatchPreviewEvent)
            return
        }
        val result = input.mouseClicked(frame, local.x, local.y, 0, ::dispatchPreviewEvent, ::openUrl)
        if (result.handled) {
            input.mouseReleased(frame, local.x, local.y, 0, ::dispatchPreviewEvent)
        }
    }

    fun press(mouseX: Float, mouseY: Float, button: Int) {
        val frame = lastFrame ?: return
        val local = localPosition(mouseX, mouseY) ?: return
        val scrollbarResult = input.scrollbarMouseClicked(frame, local.x, local.y, button, ::setScrollImmediate)
        if (scrollbarResult.handled) return
        input.mouseClicked(frame, local.x, local.y, button, ::dispatchPreviewEvent, ::openUrl)
    }

    fun drag(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float, button: Int) {
        val frame = lastFrame ?: return
        val target = lastTarget ?: return
        val local = localPosition(mouseX, mouseY) ?: return
        val scrollbarResult = input.scrollbarMouseDragged(frame, local.x, local.y, ::setScrollImmediate)
        if (scrollbarResult.handled) return
        input.mouseDragged(
            frame,
            local.x,
            local.y,
            button,
            deltaX / target.scale,
            deltaY / target.scale,
            ::dispatchPreviewEvent,
        )
    }

    fun release(mouseX: Float, mouseY: Float, button: Int) {
        val frame = lastFrame ?: return
        val local = localPosition(mouseX, mouseY) ?: return
        input.mouseReleased(frame, local.x, local.y, button, ::dispatchPreviewEvent)
    }

    fun keyInput(event: KeyEvent): Boolean {
        val frame = lastFrame ?: return false
        return when {
            event.isCharTyped -> input.charTyped(frame, event.typedChar, event.modifiers(), ::dispatchPreviewEvent).handled
            event.isPressed -> {
                val keyCode = event.glfwKeyCode()
                input.keyPressed(frame, keyCode, event.localKeyCode.code, event.modifiers(), ::dispatchPreviewEvent).handled
            }
            else -> false
        }
    }

    private fun ensureContentInstalled() {
        if (contentInstalled) return
        runtime.setContent {
            val size = contentSize.value
            ComposeBox(
                modifier = Modifier.then(
                    Modifier.layout(LayoutType.FREE),
                    Modifier.size(size.width.px, size.height.px),
                ),
            ) {
                UiXmlContent(contentTree.value, UiXmlOptions(resources = PreviewUiResourceLoader))
            }
        }
        contentInstalled = true
    }

    private fun dispatchPreviewEvent(event: UiEvent): Boolean {
        return event.node.dispatch(event)
    }

    private fun DrawContext.localMouse(target: UiRenderTarget): UiPreviewPoint? {
        return localPosition(mouseX, mouseY, this, target)
    }

    private fun localPosition(mouseX: Float, mouseY: Float): UiPreviewPoint? {
        val context = lastContext ?: return null
        val target = lastTarget ?: return null
        return localPosition(mouseX, mouseY, context, target)
    }

    private fun setScrollImmediate(node: HollowUiNode, offset: UiScrollOffset) {
        runtime.setScrollImmediate(node, offset.x, offset.y)
    }

    private fun horizontalScrollModifierDown(): Boolean {
        val window = Minecraft.getInstance().window.window
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
    }

    private fun localPosition(
        mouseX: Float,
        mouseY: Float,
        context: DrawContext,
        target: UiRenderTarget,
    ): UiPreviewPoint? {
        if (mouseX < context.x1 || mouseX > context.x2 || mouseY < context.y1 || mouseY > context.y2) return null
        return UiPreviewPoint((mouseX - context.x) / target.scale, (mouseY - context.y) / target.scale)
    }
}

private data class UiPreviewSize(
    val width: Float,
    val height: Float,
)

private data class UiPreviewPoint(
    val x: Float,
    val y: Float,
)

private fun KeyEvent.glfwKeyCode(): Int {
    return KEY_CODE_MAP.entries.firstOrNull { it.value == keyCode }?.key ?: localKeyCode.code
}

private fun KeyEvent.modifiers(): Int {
    var modifiers = 0
    if (isShiftDown) modifiers = modifiers or GLFW.GLFW_MOD_SHIFT
    if (isCtrlDown) modifiers = modifiers or GLFW.GLFW_MOD_CONTROL
    if (isAltDown) modifiers = modifiers or GLFW.GLFW_MOD_ALT
    return modifiers
}

private fun DrawContext.toRenderTarget(scale: Float): UiRenderTarget {
    val targetScale = scale.coerceIn(PreviewScaleMin, PreviewScaleMax)
    return UiRenderTarget(
        framebufferId = framebufferId,
        x = x.toInt(),
        y = framebufferHeight - y2.toInt(),
        width = width.toInt().coerceAtLeast(1),
        height = height.toInt().coerceAtLeast(1),
        logicalWidth = width / targetScale,
        logicalHeight = height / targetScale,
        scale = targetScale,
    )
}

private fun DrawContext.drawPreview(path: String, scale: Float) {
    UiPreviewRenderer.render(path, this, scale)
}

private fun setPreviewScale(value: Float) {
    val rounded = (value * PreviewScaleSteps).roundToInt() / PreviewScaleSteps
    UiPreviewState.previewScale.set(rounded.coerceIn(PreviewScaleMin, PreviewScaleMax))
}

internal object PreviewCheckerboard {
    private var texture = 0

    fun draw(context: DrawContext) {
        if (context.width <= 0f || context.height <= 0f) return
        initTexture()
        RenderSystem.setShader { GameRenderer.getPositionTexColorShader() }
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        val uMax = context.width / CheckerCellSize
        val vMax = context.height / CheckerCellSize
        val x1 = context.x
        val y1 = context.y
        val x2 = context.x2
        val y2 = context.y2
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        buffer.addVertex(x1, y1, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(x1, y2, 0f).setUv(0f, vMax).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(x2, y2, 0f).setUv(uMax, vMax).setColor(1f, 1f, 1f, 1f)
        buffer.addVertex(x2, y1, 0f).setUv(uMax, 0f).setColor(1f, 1f, 1f, 1f)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    fun initTexture() {
        if (texture != 0) return
        texture = GL11.glGenTextures()
        GlStateManager._bindTexture(texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_REPEAT)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            CheckerTextureSize,
            CheckerTextureSize,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            checkerPixels(),
        )
    }

    private fun checkerPixels(): ByteBuffer {
        val pixels = ByteBuffer.allocateDirect(CheckerTextureSize * CheckerTextureSize * 4)
        for (y in 0 until CheckerTextureSize) {
            for (x in 0 until CheckerTextureSize) {
                val color = if (x < CheckerTextureSize / 2 == y < CheckerTextureSize / 2) {
                    CheckerDark
                } else {
                    CheckerLight
                }
                pixels.put(color[0])
                pixels.put(color[1])
                pixels.put(color[2])
                pixels.put(255.toByte())
            }
        }
        pixels.flip()
        return pixels
    }
}

private const val PreviewScaleMin = 0.25f
private const val PreviewScaleMax = 4f
private const val PreviewScaleSteps = 20f
private const val CheckerCellSize = 16f
private const val CheckerTextureSize = 32
private val CheckerDark = byteArrayOf(17.toByte(), 18.toByte(), 21.toByte())
private val CheckerLight = byteArrayOf(30.toByte(), 31.toByte(), 36.toByte())

private object PreviewUiResourceLoader : UiResourceLoader, HssResourceLoader {
    override fun readText(location: String): String {
        return readLocation(ResourceLocation.parse(location))
    }

    override fun load(location: String): CompiledHss {
        return compileHss(readLocation(ResourceLocation.parse(location)))
    }

    override fun version(location: String): Long {
        return HollowUiResourceAccess.version(ResourceLocation.parse(location))
    }

    private fun readLocation(location: ResourceLocation): String {
        val local = DirectoryManager.HOLLOW_ENGINE.resolve("assets").resolve(location.namespace).resolve(location.path)
        if (Files.isRegularFile(local)) {
            return Files.readString(local, Charsets.UTF_8)
        }
        return HollowUiResourceAccess.readText(location)
    }
}

private object UiPreviewResourceHotReload {
    private var watcher: DirectoryWatcher? = null

    fun start() {
        if (watcher != null) return
        val assets = DirectoryManager.HOLLOW_ENGINE.resolve("assets")
        if (!Files.isDirectory(assets)) return
        watcher = DirectoryWatcher(assets) { path, kind ->
            if (kind == ENTRY_DELETE) return@DirectoryWatcher
            releaseTexture(path)
        }.also { it.start() }
    }

    private fun releaseTexture(path: Path) {
        val location = toResourceLocation(path) ?: return
        if (path.fileName.toString().substringAfterLast('.', "").lowercase() !in ImageExtensions) return
        RenderSystem.recordRenderCall {
            Minecraft.getInstance().textureManager.release(location)
        }
    }

    private fun toResourceLocation(path: Path): ResourceLocation? {
        val assets = DirectoryManager.HOLLOW_ENGINE.resolve("assets")
        val relative = runCatching { assets.relativize(path) }.getOrNull() ?: return null
        if (relative.nameCount < 2) return null
        val namespace = relative.getName(0).toString()
        val resourcePath = (1 until relative.nameCount).joinToString("/") { relative.getName(it).toString() }
        return runCatching { "$namespace:$resourcePath".rl }
            .onFailure { logD { "Invalid preview resource path $path" } }
            .getOrNull()
    }

    private val ImageExtensions = setOf("png", "jpg", "jpeg")
}
