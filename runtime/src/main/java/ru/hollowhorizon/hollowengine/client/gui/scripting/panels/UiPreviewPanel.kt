package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logD
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE

object UiPreviewState {
    val previewPath = mutableStateOf<String?>(null)
    val errorText = mutableStateOf<String?>(null)
}

class UiPreviewPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.ui_preview", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.CODE_EDITOR

    init {
        UiPreviewResourceHotReload.start()
    }

    override fun UiScope.compose() {
        val path = UiPreviewState.previewPath.use()
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

            GlCanvas("Hollow UI Preview", glCanvas = { drawPreview(path); surface.triggerUpdate() }) {
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
}

private object UiPreviewRenderer {
    private val runtime = HollowUiRuntime()
    private val renderer = MinecraftUiRenderer()

    fun render(path: String, context: DrawContext) {
        try {
            val target = context.toRenderTarget()
            val source = path.fromReadablePath().readText()
            val parsed = parseUi(source, UiXmlOptions(resources = PreviewUiResourceLoader))
            val root = buildRoot(parsed, context, target)
            renderer.render(
                runtime.frame(
                    root,
                    target.logicalWidth,
                    target.logicalHeight,
                    nowMillis = System.currentTimeMillis(),
                ).commands,
                target,
            )
            UiPreviewState.errorText.set(null)
        } catch (exception: Exception) {
            UiPreviewState.errorText.set(exception.message ?: exception::class.simpleName ?: "Preview error")
        }
    }

    private fun buildRoot(content: UiNode, context: DrawContext, target: UiRenderTarget): UiNode {
        return HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(target.logicalWidth.px, target.logicalHeight.px),
            ),
        ) {
            Box(
                modifier = Modifier.then(
                    Modifier.position(context.x.px, context.y.px),
                    Modifier.size(context.width.px, context.height.px),
                    Modifier.clip(),
                ),
            ) {
                Node(content)
            }
        }
    }
}

private fun DrawContext.toRenderTarget(): UiRenderTarget {
    return UiRenderTarget(
        framebufferId = framebufferId,
        x = 0,
        y = 0,
        width = framebufferWidth,
        height = framebufferHeight,
        logicalWidth = framebufferWidth.toFloat(),
        logicalHeight = framebufferHeight.toFloat(),
        scale = 1f,
    )
}

private fun DrawContext.drawPreview(path: String) {
    UiPreviewRenderer.render(path, this)
}

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
