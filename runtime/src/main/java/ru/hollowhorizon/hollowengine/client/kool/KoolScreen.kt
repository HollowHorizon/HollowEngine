package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.UiScale
import de.fabmax.kool.modules.ui2.setupUiScene
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.Scene
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.api.HudHideable
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.common.utils.literal
import kotlin.math.min

open class KoolScreen : Screen("".literal), HudHideable {
    val scene = Scene(title.string).apply {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare
        onRenderScene += {
            UiScale.uiScale.set(computeUiScale())
        }
    }

    private var isLoaded = false

    override fun init() {
        if(!isLoaded) {
            scene.setup()
            if(scene !in KoolManager.context.scenes) KoolManager.context.addScene(scene)
            isLoaded = true
        }
        super.init()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        scene.render()
    }

    protected open fun computeUiScale(): Float {
        val size = uiSize ?: return 1f
        val w = KoolManager.context.window.size.x / size.x.toFloat()
        val h = KoolManager.context.window.size.y / size.y.toFloat()
        return min(w, h) / UiScale.windowScale.value
    }

    open fun Scene.setup() {}

    override fun removed() {
        KoolManager.context.removeScene(scene)
        scene.release()
    }

    open var uiSize: Vec2i? = null
}

fun Scene.isScreenScene(): Boolean {
    (camera as? OrthographicCamera)?.let { cam ->
        if(cam.left != 0f) return false
        if(cam.top != 0f) return false
        val viewport = mainRenderPass.viewport
        if(cam.right != viewport.width.toFloat()) return false
        if(cam.bottom != -viewport.height.toFloat()) return false
    } ?: return false
    return true
}
