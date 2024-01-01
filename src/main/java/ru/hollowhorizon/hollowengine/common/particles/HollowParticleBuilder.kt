package ru.hollowhorizon.hollowengine.common.particles

import com.mojang.math.Vector3d
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleType
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.registries.RegistryObject
import ru.hollowhorizon.hollowengine.client.particles.*
import ru.hollowhorizon.hollowengine.common.network.NetworkHandler
import ru.hollowhorizon.hollowengine.common.network.SpawnParticlesPacket

import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.cos
import kotlin.math.sin


open class HollowParticleBuilder protected constructor(val type: ParticleType<*>) {
    val options: HollowParticleOptions = HollowParticleOptions(type)
    var xMotion = 0.0
    var yMotion = 0.0
    var zMotion = 0.0
    var maxXSpeed = 0.0
    var maxYSpeed = 0.0
    var maxZSpeed = 0.0
    var maxXOffset = 0.0
    var maxYOffset = 0.0
    var maxZOffset = 0.0

    fun setColorData(colorData: ParticleColor): HollowParticleBuilder {
        options.colorData = colorData
        return this
    }

    fun setScaleData(scaleData: GenericData): HollowParticleBuilder {
        options.scaleData = scaleData
        return this
    }

    fun setTransparencyData(transparencyData: GenericData): HollowParticleBuilder {
        options.transparencyData = transparencyData
        return this
    }

    fun setSpinData(spinData: GenericData): HollowParticleBuilder {
        options.spinData = spinData
        return this
    }

    fun setGravity(gravity: Float): HollowParticleBuilder {
        options.gravity = gravity
        return this
    }

    fun enableNoClip(): HollowParticleBuilder {
        options.noClip = true
        return this
    }

    fun disableNoClip(): HollowParticleBuilder {
        options.noClip = false
        return this
    }

    fun setSpritePicker(spritePicker: SpritePicker): HollowParticleBuilder {
        options.spritePicker = spritePicker
        return this
    }

    fun setDiscardFunction(discardFunctionType: DiscardType): HollowParticleBuilder {
        options.discardType = discardFunctionType
        return this
    }

//    fun setRenderType(renderType: ParticleRenderType): HollowParticleBuilder {
//        options.renderType = renderType
//        return this
//    }

    fun setLifetime(lifetime: Int): HollowParticleBuilder {
        options.lifetime = lifetime
        return this
    }

    fun setRandomMotion(maxSpeed: Double): HollowParticleBuilder {
        return this.setRandomMotion(maxSpeed, maxSpeed, maxSpeed)
    }

    fun setRandomMotion(maxHSpeed: Double, maxVSpeed: Double): HollowParticleBuilder {
        return this.setRandomMotion(maxHSpeed, maxVSpeed, maxHSpeed)
    }

    fun setRandomMotion(maxXSpeed: Double, maxYSpeed: Double, maxZSpeed: Double): HollowParticleBuilder {
        this.maxXSpeed = maxXSpeed
        this.maxYSpeed = maxYSpeed
        this.maxZSpeed = maxZSpeed
        return this
    }

    fun addMotion(vx: Double, vy: Double, vz: Double): HollowParticleBuilder {
        xMotion += vx
        yMotion += vy
        zMotion += vz
        return this
    }

    fun setMotion(vx: Double, vy: Double, vz: Double): HollowParticleBuilder {
        xMotion = vx
        yMotion = vy
        zMotion = vz
        return this
    }

    fun setRandomOffset(maxDistance: Double): HollowParticleBuilder {
        return this.setRandomOffset(maxDistance, maxDistance, maxDistance)
    }

    fun setRandomOffset(maxHDist: Double, maxVDist: Double): HollowParticleBuilder {
        return this.setRandomOffset(maxHDist, maxVDist, maxHDist)
    }

    fun setRandomOffset(maxXDist: Double, maxYDist: Double, maxZDist: Double): HollowParticleBuilder {
        maxXOffset = maxXDist
        maxYOffset = maxYDist
        maxZOffset = maxZDist
        return this
    }

    fun act(particleBuilderConsumer: Consumer<HollowParticleBuilder>): HollowParticleBuilder {
        particleBuilderConsumer.accept(this)
        return this
    }

    fun spawn(level: Level, x: Double, y: Double, z: Double): HollowParticleBuilder {
        val yaw: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
        val pitch: Double = RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
        val xSpeed: Double = RANDOM.nextFloat().toDouble() * maxXSpeed
        val ySpeed: Double = RANDOM.nextFloat().toDouble() * maxYSpeed
        val zSpeed: Double = RANDOM.nextFloat().toDouble() * maxZSpeed
        xMotion += sin(yaw) * cos(pitch) * xSpeed
        yMotion += sin(pitch) * ySpeed
        zMotion += cos(yaw) * cos(pitch) * zSpeed
        val yaw2: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
        val pitch2: Double = RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
        val xDist: Double = RANDOM.nextFloat().toDouble() * maxXOffset
        val yDist: Double = RANDOM.nextFloat().toDouble() * maxYOffset
        val zDist: Double = RANDOM.nextFloat().toDouble() * maxZOffset
        val xPos = sin(yaw2) * cos(pitch2) * xDist
        val yPos = sin(pitch2) * yDist
        val zPos = cos(yaw2) * cos(pitch2) * zDist
        NetworkHandler.HollowEngineChannel.send(
            PacketDistributor.TRACKING_CHUNK.with {
                level.getChunkAt(
                    BlockPos((x + xPos).toInt(), (y + yPos).toInt(), (z + zPos).toInt())
                )
            },
            SpawnParticlesPacket(options, x + xPos, y + yPos, z + zPos, xMotion, yMotion, zMotion)
        )
        return this
    }

    fun repeat(level: Level, x: Double, y: Double, z: Double, n: Int): HollowParticleBuilder {
        for (i in 0 until n) {
            spawn(level, x, y, z)
        }
        return this
    }

    fun surroundBlock(level: Level, pos: BlockPos, vararg dirs: Direction): HollowParticleBuilder {
        var directions = dirs
        if (directions.isEmpty()) directions = Direction.entries.toTypedArray()
        for (direction in directions) {
            val yaw: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
            val pitch: Double =
                RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
            val xSpeed: Double = RANDOM.nextFloat().toDouble() * maxXSpeed
            val ySpeed: Double = RANDOM.nextFloat().toDouble() * maxYSpeed
            val zSpeed: Double = RANDOM.nextFloat().toDouble() * maxZSpeed
            xMotion += sin(yaw) * cos(pitch) * xSpeed
            yMotion += sin(pitch) * ySpeed
            zMotion += cos(yaw) * cos(pitch) * zSpeed
            val axis = direction.axis
            val d0 = 0.5625
            val xPos =
                if (axis == Direction.Axis.X) 0.5 + d0 * direction.stepX.toDouble() else RANDOM.nextDouble()
            val yPos =
                if (axis == Direction.Axis.Y) 0.5 + d0 * direction.stepY.toDouble() else RANDOM.nextDouble()
            val zPos =
                if (axis == Direction.Axis.Z) 0.5 + d0 * direction.stepZ.toDouble() else RANDOM.nextDouble()
            NetworkHandler.HollowEngineChannel.send(
                PacketDistributor.TRACKING_CHUNK.with {
                    level.getChunkAt(
                        BlockPos((pos.x + xPos).toInt(), (pos.y + yPos).toInt(), (pos.z + zPos).toInt())
                    )
                },
                SpawnParticlesPacket(options, pos.x.toDouble() + xPos, pos.y.toDouble() + yPos, pos.z.toDouble() + zPos, xMotion, yMotion, zMotion)
            )
        }
        return this
    }

    fun repeatSurroundBlock(level: Level, pos: BlockPos, n: Int): HollowParticleBuilder {
        for (i in 0 until n) {
            surroundBlock(level, pos)
        }
        return this
    }

    fun repeatSurroundBlock(level: Level, pos: BlockPos, n: Int, vararg directions: Direction): HollowParticleBuilder {
        for (i in 0 until n) {
            surroundBlock(level, pos, *directions)
        }
        return this
    }

    fun surroundVoxelShape(level: Level, pos: BlockPos, voxelShape: VoxelShape, max: Int): HollowParticleBuilder {
        val c = IntArray(1)
        val perBoxMax = max / voxelShape.toAabbs().size
        val r = Supplier {
            c[0]++
            if (c[0] >= perBoxMax) {
                c[0] = 0
                return@Supplier true
            } else {
                return@Supplier false
            }
        }
        val v = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        voxelShape.forAllBoxes { x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double ->
            val b = v.add(x1, y1, z1)
            val e = v.add(x2, y2, z2)
            val runs = ArrayList<() -> Unit>()
            runs.add { spawnLine(level, b, v.add(x2, y1, z1)) }
            runs.add { spawnLine(level, b, v.add(x1, y2, z1)) }
            runs.add { spawnLine(level, b, v.add(x1, y1, z2)) }
            runs.add { spawnLine(level, v.add(x1, y2, z1), v.add(x2, y2, z1)) }
            runs.add { spawnLine(level, v.add(x1, y2, z1), v.add(x1, y2, z2)) }
            runs.add { spawnLine(level, e, v.add(x2, y2, z1)) }
            runs.add { spawnLine(level, e, v.add(x1, y2, z2)) }
            runs.add { spawnLine(level, e, v.add(x2, y1, z2)) }
            runs.add { spawnLine(level, v.add(x2, y1, z1), v.add(x2, y1, z2)) }
            runs.add { spawnLine(level, v.add(x1, y1, z2), v.add(x2, y1, z2)) }
            runs.add { spawnLine(level, v.add(x2, y1, z1), v.add(x2, y2, z1)) }
            runs.add { spawnLine(level, v.add(x1, y1, z2), v.add(x1, y2, z2)) }
            runs.shuffle()
            for(runnable in runs) {
                runnable()
                if (r.get()) {
                    break
                }
            }
        }
        return this
    }

    fun surroundVoxelShape(level: Level, pos: BlockPos, state: BlockState, max: Int): HollowParticleBuilder {
        var voxelShape = state.getShape(level, pos)
        if (voxelShape.isEmpty) voxelShape = Shapes.block()
        return this.surroundVoxelShape(level, pos, voxelShape, max)
    }

    fun spawnAtRandomFace(level: Level, pos: BlockPos): HollowParticleBuilder {
        val direction = Direction.entries[RANDOM.nextInt(Direction.entries.size)]
        val yaw: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
        val pitch: Double = RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
        val xSpeed: Double = RANDOM.nextFloat().toDouble() * maxXSpeed
        val ySpeed: Double = RANDOM.nextFloat().toDouble() * maxYSpeed
        val zSpeed: Double = RANDOM.nextFloat().toDouble() * maxZSpeed
        xMotion += sin(yaw) * cos(pitch) * xSpeed
        yMotion += sin(pitch) * ySpeed
        zMotion += cos(yaw) * cos(pitch) * zSpeed
        val `direction$axis` = direction.axis
        val d0 = 0.5625
        val xPos =
            if (`direction$axis` === Direction.Axis.X) 0.5 + d0 * direction.stepX.toDouble() else RANDOM.nextDouble()
        val yPos =
            if (`direction$axis` === Direction.Axis.Y) 0.5 + d0 * direction.stepY.toDouble() else RANDOM.nextDouble()
        val zPos =
            if (`direction$axis` === Direction.Axis.Z) 0.5 + d0 * direction.stepZ.toDouble() else RANDOM.nextDouble()
        NetworkHandler.HollowEngineChannel.send(
            PacketDistributor.TRACKING_CHUNK.with {
                level.getChunkAt(
                    BlockPos((pos.x + xPos).toInt(), (pos.y + yPos).toInt(), (pos.z + zPos).toInt())
                )
            },
            SpawnParticlesPacket(options, pos.x.toDouble() + xPos, pos.y.toDouble() + yPos, pos.z.toDouble() + zPos, xMotion, yMotion, zMotion)
        )
        return this
    }

    fun repeatRandomFace(level: Level, pos: BlockPos, n: Int): HollowParticleBuilder {
        for (i in 0 until n) {
            spawnAtRandomFace(level, pos)
        }
        return this
    }

    fun createCircle(
        level: Level,
        x: Double,
        y: Double,
        z: Double,
        distance: Double,
        currentCount: Double,
        totalCount: Double
    ): HollowParticleBuilder {
        val xSpeed: Double = RANDOM.nextFloat().toDouble() * maxXSpeed
        val ySpeed: Double = RANDOM.nextFloat().toDouble() * maxYSpeed
        val zSpeed: Double = RANDOM.nextFloat().toDouble() * maxZSpeed
        val theta = 6.283185307179586 / totalCount
        val finalAngle = currentCount / totalCount + theta * currentCount
        val dx2 = distance * cos(finalAngle)
        val dz2 = distance * sin(finalAngle)
        val vector2f = Vector3d(dx2, 0.0, dz2)
        xMotion = vector2f.x * xSpeed
        zMotion = vector2f.z * zSpeed
        val yaw2: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
        val pitch2: Double = RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
        val xDist: Double = RANDOM.nextFloat().toDouble() * maxXOffset
        val yDist: Double = RANDOM.nextFloat().toDouble() * maxYOffset
        val zDist: Double = RANDOM.nextFloat().toDouble() * maxZOffset
        val xPos = sin(yaw2) * cos(pitch2) * xDist
        val yPos = sin(pitch2) * yDist
        val zPos = cos(yaw2) * cos(pitch2) * zDist
        NetworkHandler.HollowEngineChannel.send(
            PacketDistributor.TRACKING_CHUNK.with {
                level.getChunkAt(
                    BlockPos((x + xPos + dx2).toInt(), (y + yPos).toInt(), (z + zPos + dz2).toInt())
                )
            },
            SpawnParticlesPacket(options, x + xPos + dx2, y + yPos, z + zPos + dz2, xMotion, ySpeed, zMotion)
        )
        return this
    }

    fun repeatCircle(
        level: Level,
        x: Double,
        y: Double,
        z: Double,
        distance: Double,
        times: Int
    ): HollowParticleBuilder {
        for (i in 0 until times) {
            createCircle(level, x, y, z, distance, i.toDouble(), times.toDouble())
        }
        return this
    }

    fun createBlockOutline(level: Level, pos: BlockPos, state: BlockState): HollowParticleBuilder {
        val voxelShape = state.getShape(level, pos)
        val d = 0.25
        voxelShape.forAllBoxes { x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double ->
            val v = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            val b = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).add(x1, y1, z1)
            val e = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).add(x2, y2, z2)
            spawnLine(level, b, v.add(x2, y1, z1))
            spawnLine(level, b, v.add(x1, y2, z1))
            spawnLine(level, b, v.add(x1, y1, z2))
            spawnLine(level, v.add(x1, y2, z1), v.add(x2, y2, z1))
            spawnLine(level, v.add(x1, y2, z1), v.add(x1, y2, z2))
            spawnLine(level, e, v.add(x2, y2, z1))
            spawnLine(level, e, v.add(x1, y2, z2))
            spawnLine(level, e, v.add(x2, y1, z2))
            spawnLine(level, v.add(x2, y1, z1), v.add(x2, y1, z2))
            spawnLine(level, v.add(x1, y1, z2), v.add(x2, y1, z2))
            spawnLine(level, v.add(x2, y1, z1), v.add(x2, y2, z1))
            spawnLine(level, v.add(x1, y1, z2), v.add(x1, y2, z2))
        }
        return this
    }

    fun spawnLine(level: Level, one: Vec3, two: Vec3?): HollowParticleBuilder {
        val yaw: Double = RANDOM.nextFloat().toDouble() * Math.PI * 2.0
        val pitch: Double = RANDOM.nextFloat().toDouble() * Math.PI - 1.5707963267948966
        val xSpeed: Double = RANDOM.nextFloat().toDouble() * maxXSpeed
        val ySpeed: Double = RANDOM.nextFloat().toDouble() * maxYSpeed
        val zSpeed: Double = RANDOM.nextFloat().toDouble() * maxZSpeed
        xMotion += sin(yaw) * cos(pitch) * xSpeed
        yMotion += sin(pitch) * ySpeed
        zMotion += cos(yaw) * cos(pitch) * zSpeed
        val pos = one.lerp(two, RANDOM.nextDouble())
        NetworkHandler.HollowEngineChannel.send(
            PacketDistributor.TRACKING_CHUNK.with {
                level.getChunkAt(
                    BlockPos(pos)
                )
            },
            SpawnParticlesPacket(options, pos.x, pos.y, pos.z, xMotion, yMotion, zMotion)
        )
        return this
    }

    companion object {
        private val RANDOM = RandomSource.create()
        fun create(type: ParticleType<*>): HollowParticleBuilder {
            return HollowParticleBuilder(type)
        }

        fun create(type: RegistryObject<*>): HollowParticleBuilder {
            return HollowParticleBuilder(type.get() as ParticleType<*>)
        }
    }
}
