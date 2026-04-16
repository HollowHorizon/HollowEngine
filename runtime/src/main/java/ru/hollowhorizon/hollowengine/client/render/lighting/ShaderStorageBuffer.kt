package ru.hollowhorizon.hollowengine.client.render.lighting

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43
import java.nio.ByteBuffer

internal class ShaderStorageBuffer(private val binding: Int) {
    private var id: Int = 0
    private var capacity: Int = 0

    fun upload(data: ByteBuffer, usage: Int = GL15.GL_STREAM_DRAW) {
        ensureCreated()
        data.flip()
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id)
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, usage)
        capacity = data.remaining()
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0)
    }

    fun ensureCapacity(requiredBytes: Int, usage: Int = GL15.GL_DYNAMIC_DRAW) {
        ensureCreated()
        val size = maxOf(requiredBytes, 4)
        if (capacity >= size) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id)
            return
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id)
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, size.toLong(), usage)
        capacity = size
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0)
    }

    fun bindBase() {
        ensureCreated()
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id)
    }

    fun release() {
        if (id == 0) return
        GL15.glDeleteBuffers(id)
        id = 0
        capacity = 0
    }

    private fun ensureCreated() {
        if (id == 0) {
            id = GL15.glGenBuffers()
        }
    }
}
