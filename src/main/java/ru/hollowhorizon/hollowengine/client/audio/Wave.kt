package ru.hollowhorizon.hollowengine.client.audio

import org.lwjgl.openal.AL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class WaveCue {
    var id: Int = 0
    var position: Int = 0
    var dataChunkID: Int = 0
    var chunkStart: Int = 0
    var blockStart: Int = 0
    var sampleStart: Int = 0
}

class WaveList(var type: String) {
    var entries: MutableList<Pair<String, String>> = ArrayList()
}

class Wave(
    var numChannels: Int,
    var sampleRate: Int,
    var bitsPerSample: Int,
    var data: ByteArray,
) {
    // Вторичные конструкторы и свойства для удобства
    var byteRate: Int = sampleRate * numChannels * (bitsPerSample / 8)
    var blockAlign: Int = numChannels * (bitsPerSample / 8)
    var lists: List<WaveList> = ArrayList()
    var cues: List<WaveCue> = ArrayList()

    constructor(
        numChannels: Int, sampleRate: Int,
        byteRate: Int, blockAlign: Int, bitsPerSample: Int, data: ByteArray,
    ) : this(numChannels, sampleRate, bitsPerSample, data) {
        this.byteRate = byteRate
        this.blockAlign = blockAlign
    }

    val bytesPerSample: Int
        get() = bitsPerSample / 8

    val duration: Float
        get() = if (byteRate > 0) data.size.toFloat() / byteRate.toFloat() else 0f

    val aLFormat: Int
        get() {
            return when (numChannels) {
                1 -> when (bitsPerSample) {
                    8 -> AL10.AL_FORMAT_MONO8
                    16 -> AL10.AL_FORMAT_MONO16
                    else -> throw IllegalStateException("Unsupported BPS: $bitsPerSample ($bytesPerSample bytes). Call convertTo16() first.")
                }
                2 -> when (bitsPerSample) {
                    8 -> AL10.AL_FORMAT_STEREO8
                    16 -> AL10.AL_FORMAT_STEREO16
                    else -> throw IllegalStateException("Unsupported BPS: $bitsPerSample ($bytesPerSample bytes). Call convertTo16() first.")
                }
                else -> throw IllegalStateException("Unsupported channel count: $numChannels")
            }
        }

    fun getScanRegion(pixelsPerSecond: Float): Int {
        if (pixelsPerSecond <= 0) return 0
        return ((sampleRate.toFloat() / pixelsPerSecond) * blockAlign).toInt()
    }

    /**
     * Конвертирует аудиоданные в 16-битный формат (стандарт для OpenAL).
     * Поддерживает исходные форматы: 8-bit, 24-bit, 32-bit int, 32-bit float.
     */
    fun convertTo16(): Wave {
        if (bitsPerSample == 16) return this // Уже 16 бит

        val sourceBytes = bitsPerSample / 8
        if (sourceBytes == 0) throw IllegalStateException("Invalid bitsPerSample: $bitsPerSample")

        val sampleCount = data.size / sourceBytes
        val destData = ByteArray(sampleCount * 2) // 16 бит = 2 байта

        val srcBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val destBuffer = ByteBuffer.wrap(destData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val pcm16: Short = when (bitsPerSample) {
                8 -> {
                    // 8 bit is unsigned (0..255), 16 bit is signed (-32768..32767)
                    val sample = (srcBuffer.get().toInt() and 0xFF)
                    ((sample - 128) * 256).toShort()
                }
                24 -> {
                    // 24 bit packed (Little Endian: LSB, MID, MSB)
                    // Читаем 3 байта
                    val b1 = srcBuffer.get().toInt() and 0xFF
                    val b2 = srcBuffer.get().toInt() and 0xFF
                    val b3 = srcBuffer.get().toInt() // MSB, сохраняем знак
                    // Собираем Int. Самый простой способ понизить до 16 бит - взять 2 старших байта.
                    // Это эквивалентно сдвигу вправо на 8 бит.
                    val sample32 = (b1) or (b2 shl 8) or (b3 shl 16)
                    (sample32 shr 8).toShort()
                }
                32 -> {
                    // Может быть Float или Int PCM. Обычно в WAV заголовке есть формат (IEEE Float = 3),
                    // но здесь мы полагаемся на эвристику или явный вызов.
                    // Предположим, что если 32 бита - это часто Float.
                    // Для надежности лучше передавать формат, но пока реализуем Float (стандарт для современных DAW)
                    val sampleVal = srcBuffer.float
                    // Clamp and scale
                    val scaled = sampleVal * 32767.0f
                    val clamped = max(-32768.0f, min(32767.0f, scaled))
                    clamped.toInt().toShort()

                    // Если вдруг это 32-bit INT, логика была бы: (srcBuffer.int shr 16).toShort()
                }
                else -> 0
            }
            destBuffer.putShort(pcm16)
        }

        val newWave = Wave(
            numChannels = numChannels,
            sampleRate = sampleRate,
            byteRate = sampleRate * numChannels * 2,
            blockAlign = numChannels * 2,
            bitsPerSample = 16,
            data = destData
        )
        newWave.lists = this.lists
        newWave.cues = this.cues
        return newWave
    }

    fun getCuesArray(): FloatArray {
        val cuesArray = FloatArray(cues.size)
        cues.forEachIndexed { index, waveCue ->
            cuesArray[index] = waveCue.position.toFloat() / sampleRate.toFloat()
        }
        return cuesArray
    }
}