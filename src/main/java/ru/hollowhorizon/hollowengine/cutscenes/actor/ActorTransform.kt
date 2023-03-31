@file:UseSerializers(ForMatrix4f::class)

package ru.hollowhorizon.hollowengine.cutscenes.actor

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.util.math.vector.Matrix4f
import ru.hollowhorizon.hc.client.utils.nbt.ForMatrix4f

@Serializable
class ActorTransform {
    val transformMap = mutableMapOf<Int, Matrix4f>()
}