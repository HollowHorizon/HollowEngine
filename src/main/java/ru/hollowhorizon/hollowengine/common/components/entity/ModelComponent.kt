package ru.hollowhorizon.hollowengine.common.components.entity

import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.models.internal.animations.headRotation
import ru.hollowhorizon.hollowengine.client.models.internal.controller.MOVEMENT_FACTOR
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.controller.calculateSpeedViaDeltaMovement
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.models.internal.v2.bindRenderer
import ru.hollowhorizon.hollowengine.common.components.isClientSide
import ru.hollowhorizon.hollowengine.common.scripting.types.LivingEntityComponent
import kotlin.math.abs

class ModelComponent(entity: LivingEntity) : LivingEntityComponent(entity) {
    init {
        if(isClientSide) {
            val model = ModelAttachment("example:models/sk_test.glb")

            if (false) {
                val head = model.child("Node_86").child("Model").child("Body").child("BodyUp").child("Head")

                model.onUpdate {
                    val speed = calculateSpeedViaDeltaMovement(owner)
                    val isMoving = abs(speed) >= MOVEMENT_FACTOR
                    animations["idle"].enabled = !isMoving
                    animations["walk"].enabled = isMoving
                    animations["walk"].speed = speed * 0.6f
                    animations["idle"].wrapMode = WrapMode.Loop
                    animations["walk"].wrapMode = WrapMode.Loop

                    head.transform.rotate(owner.headRotation)
                }
            }

            model.bindRenderer()
        }
    }
}