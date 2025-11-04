package ru.hollowhorizon.hollowengine

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import ru.hollowhorizon.hollowengine.client.models.internal.animations.ModelInstance
import ru.hollowhorizon.hollowengine.client.models.internal.animations.NodeInstance

object Models {
    object HollowEngine {
        object Entities {
            val PlayerModel = PlayerModelGltf()

            class PlayerModelGltf: ModelInstance("hollowengine:models/entity/player_model.gltf") {
                val Model = ModelNode()
                inner class ModelNode: NodeInstance(nodes["Model"].node) {
                    val Body = BodyNode()
                    inner class BodyNode: NodeInstance(nodes["Body"].node) {
                        val BodyUp = BodyUpNode()
                        inner class BodyUpNode: NodeInstance(nodes["BodyUp"].node) {
                            val Head = nodes["Head"]
                            inner class HeadNode: NodeInstance(nodes["Head"].node)
                        }
                    }
                }
            }
        }
    }

    object HollowCore {
        val Empty = EmptyGltf()

        class EmptyGltf(): ModelInstance("hollowcore:models/empty.gltf") {
            val Root = nodes["Root"]
        }
    }
}

fun main() {
    val model = Models.HollowEngine.Entities.PlayerModel.Model

    fun onUpdate() {
        model.Body.BodyUp.Head.transform.rotate(10f.deg, Vec3f.X_AXIS)
        model.Body.BodyUp.isVisible = false
    }
}