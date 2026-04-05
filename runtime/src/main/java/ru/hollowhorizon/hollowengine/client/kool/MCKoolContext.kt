package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.FrameData
import de.fabmax.kool.KoolContext
import de.fabmax.kool.util.ApplicationScope
import de.fabmax.kool.util.KoolDispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import ru.hollowhorizon.hollowengine.client.kool.gl.MCRenderBackendGl
import ru.hollowhorizon.hollowengine.client.kool.window.MCWindow
import java.awt.Desktop
import java.net.URI

class MCKoolContext : KoolContext() {
    val mcWindow = MCWindow(this)
    override val backend = MCRenderBackendGl(this)
    override val window: MCWindow get() = mcWindow
    private var nextFrameData: Deferred<FrameData>? = null

    init {
        KoolHooks.createContext(this)

        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetWindowContentScale(Minecraft.getInstance().window.window, xScale, yScale)
        mcWindow.parentScreenScale = xScale[0]
    }

    override fun getSysInfos() = emptyList<String>()
    override fun openUrl(url: String, sameWindow: Boolean) {
        Desktop.getDesktop().browse(URI(url))
    }

    override fun run() {}

    suspend fun renderFrame() {
        val frameData = nextFrameData?.await() ?: render()
        frameData.syncData()
        incrementFrameTime()
        nextFrameData = ApplicationScope.async { render() }
        KoolHooks.executeCoroutineTasks(KoolDispatchers.Backend)
        backend.renderFrame(frameData, this@MCKoolContext)
    }
}