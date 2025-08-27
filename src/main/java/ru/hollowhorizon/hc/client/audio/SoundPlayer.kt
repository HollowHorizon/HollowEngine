package ru.hollowhorizon.hc.client.audio

import net.minecraft.util.Mth
import org.joml.Vector3f
import org.lwjgl.openal.AL10

class SoundPlayer(val sound: SoundBuffer) {
    var source: Int
        private set
    var isUnique: Boolean = false
        private set

    init {
        source = AL10.alGenSources()
        AL10.alSourcei(source, AL10.AL_BUFFER, sound.buffer)
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 60.0f)
        setRelative(false)
    }

    fun canBeRemoved(): Boolean {
        return !this.isUnique && this.isStopped
    }

    fun setVolume(volume: Float) {
        AL10.alSourcef(source, AL10.AL_GAIN, volume)
    }

    fun setPitch(pitch: Float) {
        AL10.alSourcef(source, AL10.AL_PITCH, pitch)
    }

    fun setRelative(relative: Boolean) {
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, if (relative) 1 else 0)
    }

    fun setLooping(looping: Boolean) {
        AL10.alSourcei(source, AL10.AL_LOOPING, if (looping) 1 else 0)
    }

    fun setPosition(vector: Vector3f) {
        this.setPosition(vector.x, vector.y, vector.z)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        AL10.alSource3f(source, AL10.AL_POSITION, x, y, z)
    }

    fun setVelocity(vector: Vector3f) {
        this.setVelocity(vector.x, vector.y, vector.z)
    }

    fun setVelocity(x: Float, y: Float, z: Float) {
        AL10.alSource3f(source, AL10.AL_VELOCITY, x, y, z)
    }

    fun play() {
        AL10.alSourcePlay(source)
    }

    fun pause() {
        AL10.alSourcePause(source)
    }

    fun stop() {
        AL10.alSourceStop(source)
    }

    val sourceState: Int
        get() = AL10.alGetSourcei(source, 4112)

    val isPlaying: Boolean
        get() = sourceState == 4114

    val isPaused: Boolean
        get() = sourceState == 4115

    val isStopped: Boolean
        get() {
            return if (source == -1) true
            else sourceState == 4116 || sourceState == 4113
        }

    var playbackPosition: Float
        get() = AL10.alGetSourcef(source, 4132)
        set(seconds) {
            AL10.alSourcef(source, 4132, Mth.clamp(seconds, 0.0f, sound.duration))
        }

    fun delete() {
        AL10.alDeleteSources(source)
        source = -1
        sound.delete()
    }
}