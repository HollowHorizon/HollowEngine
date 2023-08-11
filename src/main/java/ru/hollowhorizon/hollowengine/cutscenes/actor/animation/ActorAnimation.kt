@file:UseSerializers(ForMatrix4f::class)

package ru.hollowhorizon.hollowengine.cutscenes.actor.animation

import com.mojang.math.Matrix4f
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.hollowhorizon.hc.client.utils.nbt.ForMatrix4f

@Serializable
class ActorAnimation {
    val animationMap = mutableMapOf<Int, ActorNodeTransform>()

    @Serializable
    data class ActorNodeTransform(val name: String, val transform: Matrix4f, val children: List<ActorNodeTransform> = listOf())
}