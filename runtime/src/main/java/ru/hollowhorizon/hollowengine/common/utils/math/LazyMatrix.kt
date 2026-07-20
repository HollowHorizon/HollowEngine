package ru.hollowhorizon.hollowengine.common.utils.math

class LazyMat4d(val update: (MutableMat4d) -> Unit) {
    var isDirty = false

    private val mat = MutableMat4d()

    fun setIdentity() {
        isDirty = false
        mat.setIdentity()
    }

    fun get(): Mat4d {
        if (isDirty) {
            update(mat)
            isDirty = false
        }
        return mat
    }
}

class LazyMat4f(val update: (MutableMat4f) -> Unit) {
    var isDirty = true

    private val mat = MutableMat4f()

    fun setIdentity() {
        isDirty = false
        mat.setIdentity()
    }

    fun get(): Mat4f {
        if (isDirty) {
            update(mat)
            isDirty = false
        }
        return mat
    }
}