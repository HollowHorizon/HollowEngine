package ru.hollowhorizon.hc.client.audio

import org.lwjgl.openal.AL10
import org.lwjgl.system.MemoryUtil

class SoundBuffer(wave: Wave) {
    var buffer: Int
        private set
    val duration: Float

    init {
        this.buffer = AL10.alGenBuffers()
        val buffer = MemoryUtil.memAlloc(wave.data.size)
        buffer.put(wave.data)
        buffer.flip()
        AL10.alBufferData(this.buffer, wave.aLFormat, buffer, wave.sampleRate)
        MemoryUtil.memFree(buffer)
        this.duration = wave.duration
    }

    fun delete() {
        AL10.alDeleteBuffers(this.buffer)
        this.buffer = -1
    }
}
