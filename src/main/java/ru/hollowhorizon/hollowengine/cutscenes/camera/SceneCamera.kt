@file:UseSerializers(ForVector3d::class, ForVector3f::class)

package ru.hollowhorizon.hollowengine.cutscenes.camera

import com.mojang.math.Vector3d
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3d
import ru.hollowhorizon.hc.client.utils.nbt.ForVector3f

@Serializable
class SceneCamera {
    private val cameraTransform = mutableMapOf<Int, Pair<Vector3d, Vector3f>>()
    private val guiOverlay = mutableMapOf<Int, GuiOverlay>()

    fun maxIndex(): Int {
        val cameraMax = cameraTransform.keys.maxOrNull() ?: 0
        val guiMax = guiOverlay.keys.maxOrNull() ?: 0
        return maxOf(cameraMax, guiMax)
    }
}