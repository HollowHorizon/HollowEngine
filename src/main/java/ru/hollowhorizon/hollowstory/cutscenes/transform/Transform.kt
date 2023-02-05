@file:UseSerializers(ForMatrix4f::class)

package ru.hollowhorizon.hollowstory.cutscenes.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.util.math.vector.Matrix4f
import ru.hollowhorizon.hc.client.utils.nbt.ForMatrix4f

@Serializable
class Transform {
    val local = Matrix4f().apply { setIdentity() }
    val global = Matrix4f().apply { setIdentity() }
}