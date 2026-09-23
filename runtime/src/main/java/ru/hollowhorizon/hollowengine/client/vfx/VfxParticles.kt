package ru.hollowhorizon.hollowengine.client.vfx

/**
 * The particles of one emitter, as flat arrays.
 *
 * Live particles are kept dense at the front: killing one moves the last into its slot, so the
 * simulation is a plain index loop with no holes.
 */
class VfxParticles(val capacity: Int) {
    var count: Int = 0
        private set

    val positionX = FloatArray(capacity)
    val positionY = FloatArray(capacity)
    val positionZ = FloatArray(capacity)

    val velocityX = FloatArray(capacity)
    val velocityY = FloatArray(capacity)
    val velocityZ = FloatArray(capacity)

    val age = FloatArray(capacity)
    val lifetime = FloatArray(capacity)

    val sizeX = FloatArray(capacity)
    val sizeY = FloatArray(capacity)
    val sizeZ = FloatArray(capacity)

    /** Euler angles in degrees. */
    val rotationX = FloatArray(capacity)
    val rotationY = FloatArray(capacity)
    val rotationZ = FloatArray(capacity)

    val colorR = FloatArray(capacity)
    val colorG = FloatArray(capacity)
    val colorB = FloatArray(capacity)
    val colorA = FloatArray(capacity)

    /** A number fixed at spawn, for variation that does not flicker from step to step. */
    val random = FloatArray(capacity)

    /** Spawn order within the emitter. */
    val index = FloatArray(capacity)

    /** The sprite-sheet frame, as a whole number held in a float. */
    val frame = FloatArray(capacity)

    /** Packed block light of the particle position, refreshed while it moves. */
    val light = IntArray(capacity)

    private var extra: MutableMap<String, FloatArray>? = null

    /** A module private array, created on first use and cleared with the emitter. */
    fun channel(name: String): FloatArray {
        val channels = extra ?: LinkedHashMap<String, FloatArray>().also { extra = it }
        return channels.getOrPut(name) { FloatArray(capacity) }
    }

    /** Claims a slot, or returns -1 when the emitter is full. */
    fun allocate(): Int {
        if (count >= capacity) return -1
        return count++
    }

    /** Kills the particle in [slot]; the last live particle takes its place. */
    fun kill(slot: Int) {
        val last = count - 1
        if (slot != last) copy(last, slot)
        count = last
    }

    fun clear() {
        count = 0
    }

    private fun copy(from: Int, to: Int) {
        positionX[to] = positionX[from]
        positionY[to] = positionY[from]
        positionZ[to] = positionZ[from]
        velocityX[to] = velocityX[from]
        velocityY[to] = velocityY[from]
        velocityZ[to] = velocityZ[from]
        age[to] = age[from]
        lifetime[to] = lifetime[from]
        sizeX[to] = sizeX[from]
        sizeY[to] = sizeY[from]
        sizeZ[to] = sizeZ[from]
        rotationX[to] = rotationX[from]
        rotationY[to] = rotationY[from]
        rotationZ[to] = rotationZ[from]
        colorR[to] = colorR[from]
        colorG[to] = colorG[from]
        colorB[to] = colorB[from]
        colorA[to] = colorA[from]
        random[to] = random[from]
        index[to] = index[from]
        frame[to] = frame[from]
        light[to] = light[from]
        extra?.values?.forEach { it[to] = it[from] }
    }
}
