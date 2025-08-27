package ru.hollowhorizon.hollowengine.client.models.internal

import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f

class Skin(
    val jointsIds: List<Int>,
    val inverseBindMatrices: Array<Mat4f>,
) {
    val joints = HashMap<Int, Node>(jointsIds.size)

    private val skin: Array<Mat4f> = Array(jointsIds.size) { MutableMat4f() }

    fun finalMatrices(node: Node): Array<Mat4f> {
        // Получаем и инвертируем глобальную матрицу узла модели
        val inverseRoot = MutableMat4f(node.globalMatrix)
        inverseRoot.invert()

        // Проходим по всем суставам
        for (i in jointsIds.indices) {
            val jointGlobalMatrix = MutableMat4f(joints[i]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
            skin[i] = MutableMat4f(inverseRoot).mul(skinMatrix).transpose() // Транспозируем уже для передачи в Minecraft
        }
        return skin
    }
}