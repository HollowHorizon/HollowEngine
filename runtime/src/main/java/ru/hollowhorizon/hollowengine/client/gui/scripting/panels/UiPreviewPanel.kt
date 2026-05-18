package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logD
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.DrawContext
import ru.hollowhorizon.hollowengine.client.kool.GlCanvas
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.render.UiRenderTarget
import ru.hollowhorizon.hollowengine.client.ui.xml.UiResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUi
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryWatcher
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
                .border(RoundRectBorder(ColorTheme.UI.BackgroundAccent, Dimensions.PaddingSmall, Dimensions.PaddingSmall))
            modifier.onClick { setPreviewScale(Minecraft.getInstance().window.guiScale.toFloat()) }
        }
    }
}

private object UiPreviewRenderer {
    private val runtime = HollowUiRuntime()
    private val renderer = MinecraftUiRenderer()
    private var hoveredKey: String? = null

    fun render(path: String, context: DrawContext, scale: Float) {
        try {
            val target = context.toRenderTarget(scale)
            val source = path.fromReadablePath().readText()
            val parsed = parseUi(source, UiXmlOptions(resources = PreviewUiResourceLoader))
            val root = buildRoot(parsed, context, target)
            UiNodeKeys.assign(root)
            applyHoverState(root)
            val now = System.currentTimeMillis()
            var frame = runtime.frame(root, target.logicalWidth, target.logicalHeight, nowMillis = now)
            val nextHovered = context.hoveredNodeKey(frame, target)
            if (nextHovered != hoveredKey) {
                hoveredKey = nextHovered
                applyHoverState(root)
                frame = runtime.frame(root, target.logicalWidth, target.logicalHeight, nowMillis = now)
            }
            PreviewCheckerboard.draw(context)
            renderer.render(frame.commands, target)
            UiPreviewState.errorText.set(null)
        } catch (exception: Exception) {
            UiPreviewState.errorText.set(exception.message ?: exception::class.simpleName ?: "Preview error")
        }
    }

    private fun buildRoot(content: UiNode, context: DrawContext, target: UiRenderTarget): UiNode {
        val scale = target.scale.coerceAtLeast(PreviewScaleMin)
        return HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(target.logicalWidth.px, target.logicalHeight.px),
            ),
        ) {
            Box(
                modifier = Modifier.then(
                    Modifier.layout(LayoutType.FREE),
                    Modifier.position((context.x / scale).px, (context.y / scale).px),
                    Modifier.size((context.width / scale).px, (context.height / scale).px),
                    Modifier.clip(),
                ),
            ) {
                Node(content)
            }
        }
    }

    private fun applyHoverState(node: UiNode) {
        val key = UiNodeKeys.key(node)
        node.states -= UiState.HOVER
        if (key == hoveredKey) node.states += UiState.HOVER
        node.children.forEach(::applyHoverState)
    }

    private fun DrawContext.hoveredNodeKey(frame: HollowUiFrame, target: UiRenderTarget): String? {
        if (mouseX < x1 || mouseX > x2 || mouseY < y1 || mouseY > y2) return null
        return frame.hitTest(mouseX / target.scale, mouseY / target.scale)?.node?.let(UiNodeKeys::key)
    }

}

private fun DrawContext.toRenderTarget(scale: Float): UiRenderTarget {
    val targetScale = scale.coerceIn(PreviewScaleMin, PreviewScaleMax)
    return UiRenderTarget(
        framebufferId = framebufferId,
        x = 0,
        y = 0,
        width = framebufferWidth,
        height = framebufferHeight,
        logicalWidth = framebufferWidth / targetScale,
        logicalHeight = framebufferHeight / targetScale,
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

private object PreviewCheckerboard {
    private var texture = 0

    fun draw(context: DrawContext) {
        if (context.width <= 0f || context.height <= 0f) return
        ensureTexture()
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

    private fun ensureTexture() {
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
                val color = if ((x < CheckerTextureSize / 2) == (y < CheckerTextureSize / 2)) {
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
