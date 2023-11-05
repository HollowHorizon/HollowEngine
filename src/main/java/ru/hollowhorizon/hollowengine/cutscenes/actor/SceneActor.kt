package ru.hollowhorizon.hollowengine.cutscenes.actor

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.cutscenes.actor.animation.ActorAnimation

@Serializable
class SceneActor(val entity: String) {
    private val actorTransform = ActorTransform()
    private val actorAnimation = ActorAnimation()

    @Transient
    var actorEntity: Entity? = null

    @Transient
    var level: Level? = null

    fun maxIndex(): Int {
        val transform = actorTransform.transformMap.keys.maxOrNull() ?: 0
        val animation = actorAnimation.animationMap.keys.maxOrNull() ?: 0
        return maxOf(transform, animation)
    }

    fun init(level: Level) {
        this.level = level

        //actorEntity = EntityType.loadEntityRecursive(generateEntityNBT(entity), level) { it }
    }

    fun update(index: Int) {
        //val transform = TransformationMatrix(actorTransform.transformMap[index])

        actorEntity?.let {
//            it.setPos(
//                transform.translation.x().toDouble(),
//                transform.translation.y().toDouble(),
//                transform.translation.z().toDouble()
//            )
//            it.xRot = transform.leftRotation.i() * transform.leftRotation.j()
//            it.yRot = transform.leftRotation.i() * transform.leftRotation.k()


        }
    }
}