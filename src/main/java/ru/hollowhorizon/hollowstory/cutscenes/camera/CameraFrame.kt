package ru.hollowhorizon.hollowstory.cutscenes.camera

import net.minecraft.client.Minecraft
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.util.math.vector.Vector3f

class CameraFrame(
    val position: Vector3d,
    val rotation: Vector3f,
    val interpolation: (Float) -> Float = { it },
    val fov: Double = Minecraft.getInstance().options.fov
)