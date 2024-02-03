package ru.hollowhorizon.hollowengine.cutscenes.camera

import com.mojang.math.Vector3d
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft

class CameraFrame(
    val position: Vector3d,
    val rotation: Vector3f,
    val interpolation: (Float) -> Float = { it },
    val fov: Int = Minecraft.getInstance().options.fov.toInt()
)