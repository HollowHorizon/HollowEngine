package ru.hollowhorizon.hollowengine.client.vfx

import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import kotlin.math.floor

/**
 * What an effect is allowed to ask about the place it is playing in.
 */
interface VfxEnvironment {
    /** Whether a point is inside something a particle cannot pass through. */
    fun isSolid(x: Double, y: Double, z: Double): Boolean

    /** The packed block and skylight at a point, for materials that are lit by the world. */
    fun lightAt(x: Double, y: Double, z: Double): Int

    companion object {
        /** An environment with nothing in it, for effects that neither collide nor read light. */
        val EMPTY = object : VfxEnvironment {
            override fun isSolid(x: Double, y: Double, z: Double) = false

            override fun lightAt(x: Double, y: Double, z: Double) = LightTexture.FULL_BRIGHT
        }
    }
}

/**
 * The world an effect is playing in.
 */
class VfxWorldEnvironment(private val level: Level) : VfxEnvironment {
    private val cursor = BlockPos.MutableBlockPos()

    override fun isSolid(x: Double, y: Double, z: Double): Boolean {
        cursor.set(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        if (!level.hasChunkAt(cursor)) return false

        val state = level.getBlockState(cursor)
        if (state.isAir) return false

        val shape = state.getCollisionShape(level, cursor)
        if (shape.isEmpty) return false

        val bounds = shape.bounds()
        val localX = x - cursor.x
        val localY = y - cursor.y
        val localZ = z - cursor.z
        return localX in bounds.minX..bounds.maxX && localY in bounds.minY..bounds.maxY && localZ in bounds.minZ..bounds.maxZ
    }

    override fun lightAt(x: Double, y: Double, z: Double): Int {
        cursor.set(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        if (!level.hasChunkAt(cursor)) return LightTexture.FULL_BRIGHT
        return LightTexture.pack(
            level.getBrightness(LightLayer.BLOCK, cursor),
            level.getBrightness(LightLayer.SKY, cursor),
        )
    }
}

/**
 * The editor preview, a floor at [floorY] and nothing else.
 */
class VfxPlaneEnvironment(private val floorY: Double = 0.0) : VfxEnvironment {
    override fun isSolid(x: Double, y: Double, z: Double): Boolean = y < floorY

    override fun lightAt(x: Double, y: Double, z: Double): Int = LightTexture.FULL_BRIGHT
}

/**
 * Smooth value noise, sampled in three dimensions and scrolling in a fourth.
 */
object VfxNoise {
    fun sample(x: Float, y: Float, z: Float, seed: Int): Float {
        val xi = floor(x.toDouble()).toInt()
        val yi = floor(y.toDouble()).toInt()
        val zi = floor(z.toDouble()).toInt()
        val xf = smooth(x - xi)
        val yf = smooth(y - yi)
        val zf = smooth(z - zi)

        val c000 = hash(xi, yi, zi, seed)
        val c100 = hash(xi + 1, yi, zi, seed)
        val c010 = hash(xi, yi + 1, zi, seed)
        val c110 = hash(xi + 1, yi + 1, zi, seed)
        val c001 = hash(xi, yi, zi + 1, seed)
        val c101 = hash(xi + 1, yi, zi + 1, seed)
        val c011 = hash(xi, yi + 1, zi + 1, seed)
        val c111 = hash(xi + 1, yi + 1, zi + 1, seed)

        val x00 = c000 + (c100 - c000) * xf
        val x10 = c010 + (c110 - c010) * xf
        val x01 = c001 + (c101 - c001) * xf
        val x11 = c011 + (c111 - c011) * xf
        val y0 = x00 + (x10 - x00) * yf
        val y1 = x01 + (x11 - x01) * yf
        return y0 + (y1 - y0) * zf
    }

    /** [octaves] layers of finer, weaker detail on top of each other. */
    fun fractal(x: Float, y: Float, z: Float, seed: Int, octaves: Int): Float {
        if (octaves <= 1) return sample(x, y, z, seed)

        var amplitude = 1f
        var frequency = 1f
        var total = 0f
        var normalizer = 0f
        repeat(octaves.coerceAtMost(4)) { octave ->
            total += sample(x * frequency, y * frequency, z * frequency, seed + octave * 7919) * amplitude
            normalizer += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        return total / normalizer
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    /** A value in -1 to 1, stable for the same cell and seed. */
    private fun hash(x: Int, y: Int, z: Int, seed: Int): Float {
        var h = seed
        h = h * 374761393 + x * 668265263
        h = h * 374761393 + y * 2147483647
        h = h * 374761393 + z * 1274126177
        h = h xor (h shr 13)
        h *= 1274126177
        h = h xor (h shr 16)
        return (h and 0xFFFF) / 32768f - 1f
    }
}
