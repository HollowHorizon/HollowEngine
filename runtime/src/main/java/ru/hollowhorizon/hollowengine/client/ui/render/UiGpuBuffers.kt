package ru.hollowhorizon.hollowengine.client.ui.render

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL31
import org.lwjgl.system.MemoryUtil
import java.nio.Buffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class UiStreamingGpuBuffer(
    private val target: Int,
    private val binding: Int? = null,
) : AutoCloseable {
    private var id = 0
    private var capacity = 0

    fun bind() = bindAs(target)

    val handle: Int
        get() {
            ensureCreated()
            return id
        }

    fun bindAs(bindingTarget: Int) {
        ensureCreated()
        GL15.glBindBuffer(bindingTarget, id)
    }

    fun bindBase() {
        binding?.let(::bindBase)
    }

    fun bindBase(index: Int) {
        bindBase(target, index)
    }

    fun bindBase(bindingTarget: Int, index: Int) {
        ensureCreated()
        GL30.glBindBufferBase(bindingTarget, index, id)
    }

    fun ensureCapacity(requiredBytes: Int) {
        bind()
        ensureStorage(requiredBytes)
        binding?.let { GL30.glBindBufferBase(target, it, id) }
        GL15.glBindBuffer(target, 0)
    }

    fun upload(data: FloatBuffer) {
        upload(data.remaining() * Float.SIZE_BYTES) { GL15.glBufferSubData(target, 0L, data) }
    }

    fun upload(data: IntBuffer) {
        upload(data.remaining() * Int.SIZE_BYTES) { GL15.glBufferSubData(target, 0L, data) }
    }

    override fun close() {
        if (id != 0) GL15.glDeleteBuffers(id)
        id = 0
        capacity = 0
    }

    private fun upload(requiredBytes: Int, write: () -> Unit) {
        bind()
        ensureStorage(requiredBytes)
        if (requiredBytes > 0) write()
        binding?.let { GL30.glBindBufferBase(target, it, id) }
        GL15.glBindBuffer(target, 0)
    }

    private fun ensureStorage(requiredBytes: Int) {
        val storageBytes = maxOf(requiredBytes, MinimumStorageBytes)
        if (storageBytes <= capacity) return
        capacity = maxOf(storageBytes, maxOf(256, capacity * 2))
        GL15.glBufferData(target, capacity.toLong(), GL15.GL_STREAM_DRAW)
    }

    private fun ensureCreated() {
        if (id == 0) id = GL15.glGenBuffers()
    }

    private companion object {
        const val MinimumStorageBytes = 16
    }
}

/** Streaming storage exposed to shaders through a GL 3.1 buffer texture. */
internal class UiStreamingTextureBuffer(
    private val internalFormat: Int,
) : AutoCloseable {
    val storage = UiStreamingGpuBuffer(GL31.GL_TEXTURE_BUFFER)
    private var texture = 0

    fun upload(data: FloatBuffer) = storage.upload(data)

    fun upload(data: IntBuffer) = storage.upload(data)

    fun ensureCapacity(requiredBytes: Int) = storage.ensureCapacity(requiredBytes)

    fun bindTexture() {
        if (texture == 0) texture = GL11.glGenTextures()
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture)
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, internalFormat, storage.handle)
    }

    override fun close() {
        if (texture != 0) GL11.glDeleteTextures(texture)
        texture = 0
        storage.close()
    }
}

internal fun FloatBuffer?.ensureUiCapacity(required: Int): FloatBuffer {
    if (this != null && capacity() >= required) return this
    this?.let(MemoryUtil::memFree)
    return MemoryUtil.memAllocFloat(maxOf(required, 64))
}

internal fun IntBuffer?.ensureUiCapacity(required: Int): IntBuffer {
    if (this != null && capacity() >= required) return this
    this?.let(MemoryUtil::memFree)
    return MemoryUtil.memAllocInt(maxOf(required, 64))
}

internal fun FloatBuffer?.prepareUiBuffer(size: Int): FloatBuffer = checkNotNull(this).apply {
    clear()
    limit(size)
}

internal fun IntBuffer?.prepareUiBuffer(size: Int): IntBuffer = checkNotNull(this).apply {
    clear()
    limit(size)
}

internal fun <T : Buffer> T?.releaseUiBuffer(): T? {
    this?.let(MemoryUtil::memFree)
    return null
}
