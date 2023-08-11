@file:UseSerializers(ForMatrix4f::class)

package ru.hollowhorizon.hollowengine.cutscenes.transform

import com.mojang.math.Matrix4f
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import ru.hollowhorizon.hc.client.utils.nbt.ForMatrix4f

@Serializable
class Transform {
    val local = Matrix4f().apply { setIdentity() }
    val global = Matrix4f().apply { setIdentity() }
}