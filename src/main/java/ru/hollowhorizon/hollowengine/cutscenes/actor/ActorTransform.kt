@file:UseSerializers(ForMatrix4f::class)

package ru.hollowhorizon.hollowengine.cutscenes.actor

import com.mojang.math.Matrix4f
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import ru.hollowhorizon.hc.client.utils.nbt.ForMatrix4f

@Serializable
class ActorTransform {
    val transformMap = mutableMapOf<Int, Matrix4f>()
}