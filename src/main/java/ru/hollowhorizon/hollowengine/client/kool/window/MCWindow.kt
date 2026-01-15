package ru.hollowhorizon.hollowengine.client.kool.window

import de.fabmax.kool.*
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.UiScale
import de.fabmax.kool.util.BufferedList
import de.fabmax.kool.util.WindowTitleHoverHandler
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.MCKoolContext

class MCWindow(val ctx: MCKoolContext) : KoolWindow {
    val windowHandle: Long = Minecraft.getInstance().window.window

    override var parentScreenScale: Float = 1f
        set(value) {
            field = value
            UiScale.updateUiScaleFromWindowScale(renderScale)
        }
    override var renderResolutionFactor: Float = 1f

    override var positionInScreen: Vec2i
        get() = Vec2i(Minecraft.getInstance().window.x, Minecraft.getInstance().window.y)
        set(value) { /* MC управляет окном */ }

    override var sizeOnScreen: Vec2i
        get() = Vec2i(Minecraft.getInstance().window.width, Minecraft.getInstance().window.height)
        set(value) { /* MC управляет окном */ }

    override val framebufferSize: Vec2i
        get() = Vec2i(Minecraft.getInstance().mainRenderTarget.width, Minecraft.getInstance().mainRenderTarget.height)

    override val size: Vec2i
        get() = Vec2i(Minecraft.getInstance().window.width, Minecraft.getInstance().window.height)

    override val renderScale: Float
        get() = parentScreenScale * renderResolutionFactor

    override var title: String
        get() = "Minecraft Kool Integration"
        set(value) {}

    override var flags: WindowFlags = WindowFlags(
        isFocused = true,
        isVisible = true,
        isMaximized = false, // Можно получить через GLFW
        isMinimized = false,
        isFullscreen = Minecraft.getInstance().options.fullscreen().get(),
        isHiddenTitleBar = false
    )

    override val capabilities: WindowCapabilities = WindowCapabilities.NONE


    // Листенеры
    override val resizeListeners = BufferedList<WindowResizeListener>()
    override val scaleChangeListeners = BufferedList<ScaleChangeListener>()
    override val flagListeners = BufferedList<WindowFlagsListener>()
    override val closeListeners = BufferedList<WindowCloseListener>()
    override val dragAndDropListeners = BufferedList<DragAndDropListener>()
    override var windowTitleHoverHandler = WindowTitleHoverHandler()

    override fun close() {}
}