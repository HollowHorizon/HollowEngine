package ru.hollowhorizon.hollowengine.client.kool.gl

import de.fabmax.kool.PassData
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.gl.ComputePassGl
import de.fabmax.kool.pipeline.backend.gl.OffscreenPass2dGl
import de.fabmax.kool.pipeline.backend.gl.OffscreenPassCubeGl
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Time
import kotlin.time.Duration.Companion.seconds

context(gl: MCRenderBackendGl)
fun PassData.executePass() {
    with(gl) {
        val pass = gpuPass
        val t = Time.precisionTime
        when (pass) {
            is Scene.ScreenPass -> mcSceneRenderer.draw(this@executePass)
            is OffscreenPass2d -> pass.impl.draw(this@executePass)
            is OffscreenPassCube -> pass.impl.draw(this@executePass)
            is ComputePass -> pass.impl.dispatch()
            else -> error("Gpu pass type not implemented: $pass")
        }
        pass.tRecord = (Time.precisionTime - t).seconds
    }
}

fun OffscreenPass2dImpl.draw(passData: PassData) = (this as OffscreenPass2dGl).draw(passData)
fun OffscreenPassCubeImpl.draw(passData: PassData) = (this as OffscreenPassCubeGl).draw(passData)
fun ComputePassImpl.dispatch() = (this as ComputePassGl).dispatch()