package ru.hollowhorizon.hollowengine.cutscenes

import kotlinx.serialization.Serializable
import net.minecraft.world.World
import ru.hollowhorizon.hollowengine.cutscenes.actor.SceneActor
import ru.hollowhorizon.hollowengine.cutscenes.camera.SceneCamera
import ru.hollowhorizon.hollowengine.cutscenes.custom.SceneScript
import ru.hollowhorizon.hollowengine.cutscenes.world.SceneWorld

@Serializable
class HollowCutscene {
    val sceneActors = mutableListOf<SceneActor>()
    val sceneCamera = SceneCamera()
    val sceneWorld = SceneWorld()
    val sceneScript = SceneScript()

    var index = 0
    val maxIndex: Int
        get() = maxOf(sceneCamera.maxIndex(), sceneWorld.maxIndex(), sceneScript.maxIndex())

    fun init(level: World) {
        sceneActors.forEach { it.init(level) }
    }

    fun update() {
        sceneActors.forEach { it.update(index) }
        if (++index > maxIndex) index = 0
    }
}