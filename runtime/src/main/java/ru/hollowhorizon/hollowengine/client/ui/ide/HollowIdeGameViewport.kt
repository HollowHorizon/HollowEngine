package ru.hollowhorizon.hollowengine.client.ui.ide

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeGameViewport.rendering
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the game panel sits, and how large the game renders for it.
 */
internal object HollowIdeGameViewport {
    @Volatile
    private var panelBounds: UiRect? = null

    @Volatile
    private var windowMetrics: RuntimeBridge.GameViewportMetrics? = null

    @Volatile
    private var rendering = false

    @Volatile
    private var windowWidth = 0

    @Volatile
    private var windowHeight = 0

    /** Called by the panel on every layout pass, and with null once it leaves the screen. */
    fun report(bounds: UiRect?) {
        panelBounds = bounds
    }

    /** Whether a docked panel is showing the game, which is the only case the game is cut down for. */
    fun isEmbedded(): Boolean = panelBounds != null

    fun windowWidth(): Int = windowWidth.takeIf { it > 0 } ?: Minecraft.getInstance().window.width

    fun windowHeight(): Int = windowHeight.takeIf { it > 0 } ?: Minecraft.getInstance().window.height

    /**
     * Makes the main game target match the physical pixels the panel occupies. The window getters
     * are answered with the panel's size only while [rendering], while the GUI dimensions stay
     * virtual until the panel disappears, so screen layout and input agree on one coordinate space.
     */
    fun beginRender(minecraft: Minecraft) {
        sampleWindowSize(minecraft)
        val requested = requestedMetrics(minecraft)
        if (requested == null) {
            restoreWindowTarget(minecraft)
            return
        }

        val target = minecraft.mainRenderTarget
        val resized = target.width != requested.framebufferWidth() || target.height != requested.framebufferHeight()
        val metricsChanged = windowMetrics != requested
        windowMetrics = requested
        if (resized) {
            target.resize(requested.framebufferWidth(), requested.framebufferHeight(), Minecraft.ON_OSX)
            minecraft.gameRenderer.resize(requested.framebufferWidth(), requested.framebufferHeight())
        }
        target.bindWrite(true)
        if (metricsChanged || resized) {
            minecraft.screen?.resize(minecraft, requested.guiScaledWidth(), requested.guiScaledHeight())
        }
        rendering = true
    }

    fun endRender() {
        rendering = false
    }

    /**
     * After game has been blitted, the screen belongs to the editor again: the game left the
     * viewport sized for the panel, and everything drawn from here on covers the whole window.
     */
    fun restoreWindowViewport() {
        if (windowMetrics == null) return
        GL11.glViewport(0, 0, windowWidth(), windowHeight())
    }

    /** Vanilla has already restored its real target during resize; recompute virtual metrics next frame. */
    fun invalidateWindowMetrics() {
        rendering = false
        windowMetrics = null
        sampleWindowSize(Minecraft.getInstance())
    }

    fun metrics(): RuntimeBridge.GameViewportMetrics? = windowMetrics

    fun isRendering(): Boolean = rendering

    private fun sampleWindowSize(minecraft: Minecraft) {
        val width = IntArray(1)
        val height = IntArray(1)
        GLFW.glfwGetFramebufferSize(minecraft.window.window, width, height)
        if (width[0] > 0 && height[0] > 0) {
            windowWidth = width[0]
            windowHeight = height[0]
        }
    }

    private fun requestedMetrics(minecraft: Minecraft): RuntimeBridge.GameViewportMetrics? {
        if (!HollowIdeOverlay.expanded) return null
        val bounds = panelBounds ?: return null
        if (bounds.width <= 0f || bounds.height <= 0f) return null

        val framebufferScale = HollowIdeScale.factor()
        val width = (bounds.width * framebufferScale).roundToInt().coerceIn(1, windowWidth().coerceAtLeast(1))
        val height = (bounds.height * framebufferScale).roundToInt().coerceIn(1, windowHeight().coerceAtLeast(1))
        val guiScale = viewportGuiScale(minecraft, width, height)
        return RuntimeBridge.GameViewportMetrics(
            width,
            height,
            ceil(width / guiScale).toInt().coerceAtLeast(1),
            ceil(height / guiScale).toInt().coerceAtLeast(1),
            guiScale,
        )
    }

    private fun viewportGuiScale(minecraft: Minecraft, width: Int, height: Int): Double {
        val requested = minecraft.options.guiScale().get()
        var scale = 1
        while (scale != requested && scale < width && scale < height && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++
        }
        if (minecraft.isEnforceUnicode && scale % 2 != 0) scale++
        return scale.toDouble()
    }

    private fun restoreWindowTarget(minecraft: Minecraft) {
        if (windowMetrics == null) {
            rendering = false
            return
        }
        rendering = false
        windowMetrics = null
        val width = windowWidth().coerceAtLeast(1)
        val height = windowHeight().coerceAtLeast(1)
        val target = minecraft.mainRenderTarget
        if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX)
            minecraft.gameRenderer.resize(width, height)
        }
        val window = minecraft.window
        minecraft.screen?.resize(minecraft, window.guiScaledWidth, window.guiScaledHeight)
        target.bindWrite(true)
    }

    fun imageRect(bounds: UiRect): UiRect? {
        val target = Minecraft.getInstance().mainRenderTarget
        if (target == null || target.width <= 0 || target.height <= 0) return null
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val scale = min(bounds.width / target.width, bounds.height / target.height)
        val width = target.width * scale
        val height = target.height * scale
        return UiRect(
            x = bounds.x + (bounds.width - width) * 0.5f,
            y = bounds.y + (bounds.height - height) * 0.5f,
            width = width,
            height = height,
        )
    }

    fun imageRect(): UiRect? = panelBounds?.let(::imageRect)
}
